from __future__ import annotations

import json
import os
import re
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, AsyncIterator
from urllib.parse import urljoin

from qqmusic_api import Client, Credential
from qqmusic_api.modules.song import SongFileInfo, SongFileType

from .schemas import Comment, Lyrics, Playlist, Song, SongDetail, SongUrl, UserProfile

_SONG_ID_PREFIX = "qqmusic:"
_DEFAULT_STREAM_DOMAIN = "https://isure.stream.qqmusic.qq.com/"
_LRC_TIMESTAMP_PATTERN = re.compile(r"\[[0-9:.]+\]")
_WINDOWS_DRIVE_PATTERN = re.compile(r"^([A-Za-z]):[\\/](.*)$")


class QQMusicClient:
    """Thin adapter around qqmusic-api-python."""

    async def search(self, keyword: str, limit: int) -> list[Song]:
        async with self._client() as client:
            result = await client.search.search_by_type(keyword=keyword, num=limit)
            return [self._to_song(song) for song in result.song[:limit]]

    async def song(self, song_id: str) -> SongDetail:
        async with self._client() as client:
            track = await self._resolve_song(client, song_id)
            return self._to_song_detail(track)

    async def song_url(self, song_id: str) -> SongUrl:
        async with self._client() as client:
            track = await self._resolve_song(client, song_id)
            file_info = SongFileInfo(
                mid=track.mid,
                song_type=track.type,
                media_mid=track.file.media_mid or None,
            )
            for file_type in (SongFileType.MP3_320, SongFileType.MP3_128, SongFileType.ACC_96):
                response = await client.song.get_song_urls([file_info], file_type=file_type)
                if not response.data:
                    continue
                item = response.data[0]
                if item.result != 0 or not item.purl:
                    continue
                return SongUrl(
                    song_id=self._song_identity(track),
                    url=item.purl if item.purl.startswith("http") else urljoin(_DEFAULT_STREAM_DOMAIN, item.purl),
                    expires_in_seconds=response.expiration or None,
                )
            return SongUrl(song_id=self._song_identity(track))

    async def lyrics(self, song_id: str) -> Lyrics:
        async with self._client() as client:
            value = self._qqmusic_value(song_id)
            result = (await client.lyric.get_lyric(value, trans=True)).decrypt()
            synced_text = result.lyric or ""
            return Lyrics(
                song_id=song_id,
                plain_text=self._strip_lrc_timestamps(synced_text),
                synced_text=synced_text,
            )

    async def comments(self, song_id: str) -> list[Comment]:
        async with self._client() as client:
            track = await self._resolve_song(client, song_id)
            result = await client.comment.get_hot_comments(track.id, page_size=20)
            return [
                Comment(
                    id=item.cmid,
                    song_id=self._song_identity(track),
                    author_name=item.nick,
                    text=item.content,
                    liked_count=item.praise_num,
                    created_at=self._timestamp_to_iso(item.pub_time),
                )
                for item in result.comments
            ]

    async def profile(self) -> UserProfile:
        credential = self._credential()
        if credential is None:
            return UserProfile(id="local", display_name="Local QQ Music user")

        async with self._client(credential) as client:
            if credential.encrypt_uin:
                homepage = await client.user.get_homepage(credential.encrypt_uin, credential=credential)
                return UserProfile(
                    id=str(credential.musicid or credential.str_musicid or "local"),
                    display_name=homepage.base_info.name or "QQ Music user",
                    avatar_url=homepage.base_info.avatar or None,
                )
            return UserProfile(
                id=str(credential.musicid or credential.str_musicid or "local"),
                display_name="QQ Music user",
            )

    async def playlists(self) -> list[Playlist]:
        credential = self._credential()
        if credential is None or not credential.musicid:
            return []

        async with self._client(credential) as client:
            result = await client.user.get_created_songlist(credential.musicid, credential=credential)
            return [self._to_playlist(item) for item in result.playlists]

    async def playlist_songs(self, playlist_id: str) -> list[Song]:
        async with self._client() as client:
            result = await client.songlist.get_detail(int(self._qqmusic_value(playlist_id)), num=100, onlysong=True)
            return [self._to_song(song) for song in result.songs]

    @asynccontextmanager
    async def _client(self, credential: Credential | None = None) -> AsyncIterator[Client]:
        device_path = self._device_path()
        device_path.parent.mkdir(parents=True, exist_ok=True)
        async with Client(
            credential=credential or self._credential(),
            device_path=device_path,
            enable_sign=False,
            proxy=os.environ.get("MUSIO_QQMUSIC_PROXY") or None,
        ) as client:
            yield client

    async def _resolve_song(self, client: Client, song_id: str):
        value = self._qqmusic_value(song_id)
        detail = await client.song.get_detail(value)
        return detail.track

    def _credential(self) -> Credential | None:
        path = self._credential_path()
        if not path.exists():
            return None

        data = self._read_json(path)
        extra = data.get("extraFields") if isinstance(data.get("extraFields"), dict) else {}
        merged = {**extra, **data}
        credential_data = {
            "openid": merged.get("openid", ""),
            "refresh_token": merged.get("refresh_token") or merged.get("refreshToken", ""),
            "access_token": merged.get("access_token") or merged.get("accessToken", ""),
            "expired_at": self._epoch_value(merged.get("expired_at") or merged.get("expiredAt")),
            "musicid": self._int_value(merged.get("musicid") or merged.get("str_musicid") or merged.get("strMusicid")),
            "musickey": merged.get("musickey", ""),
            "unionid": merged.get("unionid", ""),
            "str_musicid": merged.get("str_musicid") or merged.get("strMusicid") or str(merged.get("musicid", "")),
            "refresh_key": merged.get("refresh_key") or merged.get("refreshKey", ""),
            "musickeyCreateTime": self._int_value(merged.get("musickeyCreateTime")),
            "keyExpiresIn": self._int_value(merged.get("keyExpiresIn")),
            "encryptUin": merged.get("encryptUin", ""),
            "loginType": self._int_value(merged.get("loginType")),
        }
        return Credential.model_validate(credential_data)

    def _credential_path(self) -> Path:
        configured = os.environ.get("MUSIO_QQMUSIC_CREDENTIALS")
        if configured:
            return self._normalize_path(configured)
        return self._musio_home() / "credentials" / "qqmusic.json"

    def _device_path(self) -> Path:
        configured = os.environ.get("MUSIO_QQMUSIC_DEVICE_PATH")
        if configured:
            return self._normalize_path(configured)
        return self._musio_home() / "qqmusic-device.json"

    def _musio_home(self) -> Path:
        configured = os.environ.get("MUSIO_HOME")
        if configured:
            return self._normalize_path(configured)

        config_path = self._normalize_path(os.environ.get("MUSIO_CONFIG", "~/.musio/config.toml"))
        storage_home = self._toml_value(config_path, "storage", "home")
        if storage_home:
            return self._normalize_path(storage_home)
        return self._normalize_path("~/.musio")

    def _toml_value(self, path: Path, section: str, key: str) -> str | None:
        if not path.exists():
            return None

        current_section = ""
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.split("#", 1)[0].strip()
            if not line:
                continue
            if line.startswith("[") and line.endswith("]"):
                current_section = line[1:-1].strip()
                continue
            if current_section != section or "=" not in line:
                continue
            name, value = line.split("=", 1)
            if name.strip() != key:
                continue
            return value.strip().strip('"').strip("'")
        return None

    def _normalize_path(self, value: str) -> Path:
        value = os.path.expanduser(value)
        match = _WINDOWS_DRIVE_PATTERN.match(value)
        if os.name != "nt" and match:
            drive = match.group(1).lower()
            rest = match.group(2).replace("\\", "/")
            return Path(f"/mnt/{drive}/{rest}")
        return Path(value)

    def _read_json(self, path: Path) -> dict[str, Any]:
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        if not isinstance(payload, dict):
            raise ValueError(f"Expected JSON object in {path}")
        return payload

    def _qqmusic_value(self, value: str) -> str | int:
        raw = value.removeprefix(_SONG_ID_PREFIX)
        return int(raw) if raw.isdigit() else raw

    def _to_song(self, song: Any) -> Song:
        return Song(
            id=self._song_identity(song),
            title=song.title or song.name,
            artists=[artist.name for artist in song.singer if artist.name],
            album=song.album.name or song.album.title or None,
            duration_seconds=song.interval or None,
            artwork_url=song.cover_url() or None,
        )

    def _to_song_detail(self, song: Any) -> SongDetail:
        base = self._to_song(song)
        return SongDetail(
            id=base.id,
            title=base.title,
            artists=base.artists,
            album=base.album,
            duration_seconds=base.duration_seconds,
            artwork_url=base.artwork_url,
            source_url=f"https://y.qq.com/n/ryqq/songDetail/{song.mid}" if song.mid else None,
        )

    def _to_playlist(self, playlist: Any) -> Playlist:
        return Playlist(
            id=f"{_SONG_ID_PREFIX}{playlist.id}",
            name=playlist.title,
            song_count=playlist.songnum or None,
            artwork_url=playlist.picurl or None,
        )

    def _song_identity(self, song: Any) -> str:
        return f"{_SONG_ID_PREFIX}{song.mid or song.id}"

    def _strip_lrc_timestamps(self, text: str) -> str:
        lines = []
        for line in text.splitlines():
            cleaned = _LRC_TIMESTAMP_PATTERN.sub("", line).strip()
            if cleaned:
                lines.append(cleaned)
        return "\n".join(lines)

    def _timestamp_to_iso(self, value: int) -> str | None:
        if not value:
            return None
        return datetime.fromtimestamp(value, UTC).isoformat().replace("+00:00", "Z")

    def _int_value(self, value: Any) -> int:
        if value is None or value == "":
            return 0
        return int(value)

    def _epoch_value(self, value: Any) -> int:
        if value is None or value == "":
            return 0
        if isinstance(value, int):
            return value
        if isinstance(value, float):
            return int(value)
        try:
            return int(value)
        except (TypeError, ValueError):
            return int(datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp())
