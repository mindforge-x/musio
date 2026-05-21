from __future__ import annotations

import json
import os
import re
from collections import Counter
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, AsyncIterator, Awaitable, Callable
from urllib.parse import urljoin

from qqmusic_api import Client, Credential
from qqmusic_api.modules.search import SearchType
from qqmusic_api.modules.song import SongFileInfo, SongFileType

from .schemas import (
    Album,
    Artist,
    Chart,
    Comment,
    Lyrics,
    PlaybackResolution,
    Playlist,
    Song,
    SongDetail,
    SongUrl,
    TrackPlayability,
    TrackPlayabilityQuality,
    UserConnectionStatus,
    UserMusicGene,
    UserProfile,
)

_SONG_ID_PREFIX = "qqmusic:"
_ALBUM_ID_PREFIX = "qqmusic:album:"
_ARTIST_ID_PREFIX = "qqmusic:artist:"
_CHART_ID_PREFIX = "qqmusic:chart:"
_DEFAULT_STREAM_DOMAIN = "https://isure.stream.qqmusic.qq.com/"
_PHOTO_NEW_DOMAIN = "https://y.gtimg.cn/music/photo_new/"
_PHOTO_NEW_SIZE_SEGMENT = "R300x300"
_LRC_TIMESTAMP_PATTERN = re.compile(r"\[[0-9:.]+\]")
_WINDOWS_DRIVE_PATTERN = re.compile(r"^([A-Za-z]):[\\/](.*)$")
_MUSIC_GENE_LIKED_SONG_LIMIT = 80
_MUSIC_GENE_LIST_LIMIT = 30
_MUSIC_GENE_SIGNAL_LIMIT = 50
_HOT_COMMENT_PAGE_SIZE = 15
_PLAYABILITY_FILE_TYPES = (SongFileType.MP3_320, SongFileType.MP3_128, SongFileType.ACC_96)


class QQMusicClient:
    """Thin adapter around qqmusic-api-python."""

    async def search(self, keyword: str, limit: int) -> list[Song]:
        async with self._client() as client:
            result = await self._raw_response(client.search.search_by_type(keyword=keyword, num=limit))
            return [self._to_song(song) for song in self._items_at(result, "body", "item_song")[:limit]]

    async def song(self, song_id: str) -> SongDetail:
        async with self._client() as client:
            track = await self._resolve_song(client, song_id)
            return self._to_song_detail(track)

    async def song_url(self, song_id: str) -> SongUrl:
        async with self._client() as client:
            track = await self._resolve_song(client, song_id)
            file_info = SongFileInfo(
                mid=self._text(self._value(track, "mid", "songmid", "songMid")),
                song_type=self._int_value(self._value(track, "type")),
                media_mid=self._text(self._value(self._value(track, "file"), "media_mid", "mediaMid")) or None,
            )
            for file_type in (SongFileType.MP3_320, SongFileType.MP3_128, SongFileType.ACC_96):
                response = await self._raw_response(client.song.get_song_urls([file_info], file_type=file_type))
                items = self._items_at(response, "midurlinfo")
                if not items:
                    continue
                item = items[0]
                purl = self._text(self._value(item, "purl"))
                if self._int_value(self._value(item, "result")) != 0 or not purl:
                    continue
                return SongUrl(
                    song_id=self._song_identity(track),
                    url=purl if purl.startswith("http") else urljoin(_DEFAULT_STREAM_DOMAIN, purl),
                    expires_in_seconds=self._positive_int(self._value(response, "expiration")),
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
            result = await self._comment_response(client.comment.get_hot_comments(self._song_numeric_id(track), page_size=_HOT_COMMENT_PAGE_SIZE))
            comments = self._comment_items(result, self._song_identity(track), "hot")
            return self._rank_hot_comments(comments)

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

    async def connection_status(self) -> UserConnectionStatus:
        credential = self._credential()
        checked_at = self._now_iso()
        if credential is None:
            return UserConnectionStatus(
                state="NOT_LOGGED_IN",
                credential_stored=False,
                authenticated=False,
                message="QQ Music credential is not stored.",
                checked_at=checked_at,
            )

        user_id = str(credential.musicid or credential.str_musicid or "local")
        try:
            async with self._client(credential) as client:
                await client.user.get_vip_info(credential=credential)
                profile = await self._profile_from_credential(client, credential)
                return UserConnectionStatus(
                    state="CONNECTED",
                    credential_stored=True,
                    authenticated=True,
                    user_id=profile.id,
                    display_name=profile.display_name,
                    message="QQ Music credential is valid.",
                    checked_at=checked_at,
                )
        except Exception as error:
            return UserConnectionStatus(
                state="EXPIRED",
                credential_stored=True,
                authenticated=False,
                user_id=user_id,
                message=f"QQ Music credential validation failed: {type(error).__name__}",
                checked_at=checked_at,
            )

    async def music_gene(self) -> UserMusicGene:
        credential = self._credential()
        if credential is None:
            raise PermissionError("QQ Music credential is not stored.")
        if not credential.encrypt_uin:
            raise PermissionError("QQ Music credential does not contain encrypted UIN.")

        async with self._client(credential) as client:
            data = await self._build_music_gene(client, credential)
            return UserMusicGene(
                user_id=str(credential.musicid or credential.str_musicid or "local"),
                euin=credential.encrypt_uin,
                generated_at=self._now_iso(),
                data=data,
            )

    async def playlists(self) -> list[Playlist]:
        credential = self._credential()
        if credential is None or not credential.musicid:
            return []

        async with self._client(credential) as client:
            result = await self._created_songlist_response(client, credential)
            return [self._to_playlist(item) for item in self._created_songlist_items(result)]

    async def playlist_songs(self, playlist_id: str) -> list[Song]:
        async with self._client() as client:
            result = await self._songlist_detail_response(
                client,
                playlist_id,
                num=100,
                onlysong=True,
            )
            return [self._to_song(song) for song in self._songlist_tracks(result)]

    async def track_playability(self, track_id: str) -> TrackPlayability:
        checked_at = self._now_iso()
        async with self._client() as client:
            try:
                track = await self._resolve_song(client, track_id)
            except Exception:
                return TrackPlayability(
                    track_id=track_id,
                    playable=False,
                    state="NOT_FOUND",
                    reason="Track detail could not be resolved.",
                    checked_at=checked_at,
                )

            file_info = self._song_file_info(track)
            quality_results: list[TrackPlayabilityQuality] = []
            for file_type in _PLAYABILITY_FILE_TYPES:
                quality_results.append(await self._quality_playability(client, file_info, file_type, track))

            best = next((item.quality for item in quality_results if item.playable), None)
            state = "PLAYABLE" if best else self._fallback_track_state(track, quality_results)
            reason = "" if best else self._fallback_track_reason(state, quality_results)
            return TrackPlayability(
                track_id=self._song_identity(track),
                playable=best is not None,
                state=state,
                reason=reason,
                best_quality=best,
                quality_results=quality_results,
                checked_at=checked_at,
            )

    async def resolve_playback(self, track_id: str) -> PlaybackResolution:
        song_url = await self.song_url(track_id)
        if song_url.url:
            return PlaybackResolution(
                track_id=song_url.song_id,
                playback_mode="STREAM_URL",
                stream_url=song_url.url,
                expires_in_seconds=song_url.expires_in_seconds,
                state="PLAYABLE",
            )
        playability = await self.track_playability(track_id)
        return PlaybackResolution(
            track_id=playability.track_id,
            playback_mode="UNSUPPORTED",
            provider_uri=playability.track_id,
            web_url=self._track_web_url(playability.track_id),
            state=playability.state,
            unsupported_reason=playability.reason or playability.state,
        )

    async def playlist_detail(self, playlist_id: str) -> dict[str, Any]:
        async with self._client() as client:
            result = await self._songlist_detail_response(client, playlist_id, num=1, page=1)
            return self._to_playlist_detail(self._value(result, "dirinfo"), self._response_total(result, 0))

    async def playlist_tracks(self, playlist_id: str, limit: int, page: int) -> list[Song]:
        async with self._client() as client:
            result = await self._songlist_detail_response(
                client,
                playlist_id,
                num=limit,
                page=page,
                onlysong=True,
            )
            return [self._to_song(song) for song in self._songlist_tracks(result)[:limit]]

    async def search_artists(self, keyword: str, limit: int, page: int) -> list[Artist]:
        async with self._client() as client:
            result = await self._raw_response(client.search.search_by_type(
                keyword=keyword,
                search_type=SearchType.SINGER,
                num=limit,
                page=page,
            ))
            return [self._to_artist(artist) for artist in self._items_at(result, "body", "singer")[:limit]]

    async def artist_detail(self, artist_id: str) -> Artist:
        mid = str(self._typed_value(artist_id, "artist"))
        async with self._client() as client:
            info = await self._raw_response(client.singer.get_info(mid))
            description = ""
            try:
                desc = await self._raw_response(client.singer.get_desc([mid]))
                first = self._items_at(desc, "singer_list")[0] if self._items_at(desc, "singer_list") else None
                description = self._text(self._value(self._value(first, "ex_info"), "desc")) if first is not None else ""
            except Exception:
                description = ""
            artist = self._to_artist(self._value(self._value(info, "Info"), "Singer"))
            base_info = self._value(self._value(info, "Info"), "BaseInfo")
            return Artist(
                id=artist.id,
                name=artist.name or self._text(self._value(base_info, "name", "Name")),
                avatar_url=self._text(self._value(base_info, "avatar", "Avatar")) or artist.avatar_url,
                alias=artist.alias,
                genres=artist.genres,
                description=description or None,
                song_count=artist.song_count,
                album_count=artist.album_count,
            )

    async def artist_tracks(self, artist_id: str, limit: int, page: int) -> list[Song]:
        async with self._client() as client:
            result = await self._raw_response(
                client.singer.get_songs_list(str(self._typed_value(artist_id, "artist")), num=limit, page=page),
            )
            return [self._to_song(song) for song in self._items_at(result, "songList", "*", "songInfo")[:limit]]

    async def search_albums(self, keyword: str, limit: int, page: int) -> list[Album]:
        async with self._client() as client:
            result = await self._raw_response(client.search.search_by_type(
                keyword=keyword,
                search_type=SearchType.ALBUM,
                num=limit,
                page=page,
            ))
            return [self._to_album(album) for album in self._items_at(result, "body", "item_album")[:limit]]

    async def album_detail(self, album_id: str) -> Album:
        async with self._client() as client:
            result = await self._raw_response(client.album.get_detail(self._typed_value(album_id, "album")))
            album_data = self._value(result, "basicInfo")
            album = self._to_album(album_data)
            return Album(
                id=album.id,
                title=album.title,
                artists=self._album_artist_names(result) or album.artists,
                artwork_url=album.artwork_url,
                release_date=album.release_date,
                song_count=album.song_count,
                description=self._text(self._value(album_data, "desc", "description")) or None,
            )

    async def album_tracks(self, album_id: str, limit: int, page: int) -> list[Song]:
        async with self._client() as client:
            result = await self._raw_response(client.album.get_song(self._typed_value(album_id, "album"), num=limit, page=page))
            return [self._to_song(song) for song in self._items_at(result, "songList", "*", "songInfo")[:limit]]

    async def search_playlists(self, keyword: str, limit: int, page: int) -> list[Playlist]:
        async with self._client() as client:
            result = await self._raw_response(client.search.search_by_type(
                keyword=keyword,
                search_type=SearchType.SONGLIST,
                num=limit,
                page=page,
            ))
            return [self._to_playlist(item) for item in self._items_at(result, "body", "item_songlist")[:limit]]

    async def track_comments(self, track_id: str, comment_type: str, limit: int, page: int, cursor: str) -> dict[str, Any]:
        async with self._client() as client:
            track = await self._resolve_song(client, track_id)
            normalized_type = comment_type.strip().lower()
            if normalized_type == "new":
                result = await self._comment_response(
                    client.comment.get_new_comments(self._song_numeric_id(track), page_num=page, page_size=limit, last_comment_seq_no=cursor),
                )
            elif normalized_type == "recommend":
                result = await self._comment_response(
                    client.comment.get_recommend_comments(self._song_numeric_id(track), page_num=page, page_size=limit, last_comment_seq_no=cursor),
                )
            elif normalized_type == "moment":
                result = await self._comment_response(
                    client.comment.get_moment_comments(self._song_numeric_id(track), page_size=limit, last_comment_seq_no=cursor),
                )
            else:
                normalized_type = "hot"
                result = await self._comment_response(
                    client.comment.get_hot_comments(self._song_numeric_id(track), page_num=page, page_size=limit, last_comment_seq_no=cursor),
                )
            comments = self._comment_items(result, self._song_identity(track), normalized_type)[:limit]
            return {
                "trackId": self._song_identity(track),
                "type": normalized_type,
                "count": len(comments),
                "comments": self._rank_hot_comments(comments) if normalized_type == "hot" else comments,
                "hasMore": self._comment_has_more(result, normalized_type),
                "nextCursor": self._comment_next_cursor(result, comments, normalized_type),
            }

    async def recommend_tracks(self, recommend_type: str, limit: int, page: int) -> list[Song]:
        normalized_type = recommend_type.strip().lower()
        async with self._client() as client:
            if normalized_type == "new":
                result = await self._raw_response(client.recommend.get_recommend_newsong())
                return [self._to_song(song) for song in self._items_at(result, "songlist")[:limit]]
            if normalized_type == "radar":
                result = await self._raw_response(client.recommend.get_radar_recommend(page=page))
                return [self._to_song(song) for song in self._items_at(result, "VecSongs", "*", "Track")[:limit]]
            result = await self._raw_response(client.recommend.get_guess_recommend())
            return [self._to_song(song) for song in self._items_at(result, "Tracks")[:limit]]

    async def recommend_playlists(self, limit: int, page: int) -> list[Playlist]:
        async with self._client() as client:
            result = await self._raw_response(client.recommend.get_recommend_songlist(page=page, num=limit))
            return [self._to_playlist(item) for item in self._recommend_playlist_items(result)[:limit]]

    async def chart_categories(self) -> dict[str, Any]:
        async with self._client() as client:
            result = await client.top.get_category()
            categories = []
            charts = []
            for category in result.group:
                category_charts = [self._to_chart(item) for item in category.toplist]
                categories.append({
                    "id": str(category.id),
                    "name": category.name,
                    "charts": category_charts,
                })
                charts.extend(category_charts)
            return {"count": len(charts), "categories": categories, "charts": charts}

    async def chart_detail(self, chart_id: str, limit: int, page: int) -> dict[str, Any]:
        async with self._client() as client:
            resolved_chart_id = await self._resolve_chart_id(client, chart_id)
            result = await self._raw_response(client.top.get_detail(resolved_chart_id, num=limit, page=page))
            tracks = [self._to_song(song) for song in self._items_at(result, "songInfoList")[:limit]]
            return {
                "chart": self._to_chart(self._value(result, "data")),
                "count": len(tracks),
                "tracks": tracks,
                "songs": tracks,
            }

    async def _resolve_chart_id(self, client: Client, chart_id: str) -> int:
        value = self._typed_value(chart_id, "chart")
        numeric_id = self._chart_numeric_id(value)
        if numeric_id is not None:
            return numeric_id

        requested = self._normalize_lookup_text(chart_id)
        if not requested:
            raise ValueError("chartId must not be empty.")

        categories = await client.top.get_category()
        category_matches: list[Any] = []
        for category in getattr(categories, "group", []) or []:
            charts = getattr(category, "toplist", []) or []
            if self._normalize_lookup_text(getattr(category, "name", "")) == requested:
                category_matches.extend(charts)
            for chart in charts:
                if self._chart_matches(chart, requested):
                    chart_numeric_id = self._chart_numeric_id(self._value(chart, "id", "topId"))
                    if chart_numeric_id is not None:
                        return chart_numeric_id
        if len(category_matches) == 1:
            chart_numeric_id = self._chart_numeric_id(self._value(category_matches[0], "id", "topId"))
            if chart_numeric_id is not None:
                return chart_numeric_id
        if category_matches:
            names = "、".join(
                self._text(self._value(chart, "name", "title"))
                for chart in category_matches[:8]
                if self._text(self._value(chart, "name", "title"))
            )
            raise ValueError(f"chartId '{chart_id}' is a chart category, not a chart id. Choose one chart from: {names}.")
        raise ValueError(f"Unknown QQ Music chartId: {chart_id}. Use get_chart_categories first and pass a chart id.")

    async def _build_music_gene(self, client: Client, credential: Credential) -> dict[str, Any]:
        euin = credential.encrypt_uin
        errors: list[dict[str, str]] = []

        raw_report = await self._optional_music_gene_source(
            "qq_music_gene_report",
            errors,
            lambda: client.user.get_music_gene(euin, credential=credential),
        )
        liked_result = await self._optional_music_gene_source(
            "liked_songs",
            errors,
            lambda: self._raw_response(client.user.get_fav_song(
                euin,
                page=1,
                num=_MUSIC_GENE_LIKED_SONG_LIMIT,
                credential=credential,
            )),
        )
        favorite_songlist_result = await self._optional_music_gene_source(
            "favorite_playlists",
            errors,
            lambda: self._raw_response(client.user.get_fav_songlist(
                euin,
                page=1,
                num=_MUSIC_GENE_LIST_LIMIT,
                credential=credential,
            )),
        )
        favorite_album_result = await self._optional_music_gene_source(
            "favorite_albums",
            errors,
            lambda: self._raw_response(client.user.get_fav_album(
                euin,
                page=1,
                num=_MUSIC_GENE_LIST_LIMIT,
                credential=credential,
            )),
        )
        follow_singer_result = await self._optional_music_gene_source(
            "follow_singers",
            errors,
            lambda: self._raw_response(client.user.get_follow_singers(
                euin,
                page=1,
                num=_MUSIC_GENE_LIST_LIMIT,
                credential=credential,
            )),
        )

        created_result = None
        if credential.musicid:
            created_result = await self._optional_music_gene_source(
                "created_playlists",
                errors,
                lambda: self._created_songlist_response(client, credential),
            )

        liked_songs = self._songlist_tracks(liked_result)
        created_playlists = self._created_songlist_items(created_result)
        favorite_playlists = self._items_at(favorite_songlist_result, "v_list")
        favorite_albums = self._items_at(favorite_album_result, "v_list")
        follow_singers = self._items_at(follow_singer_result, "List")

        top_artists = self._rank_top_artists(liked_songs, favorite_albums, follow_singers)
        top_albums = self._rank_top_albums(liked_songs, favorite_albums)
        source_signal_count = len(liked_songs) + len(favorite_albums) + len(follow_singers)

        raw: dict[str, Any] = {}
        if raw_report is not None:
            raw["qq_music_gene_report"] = self._jsonable(raw_report)

        return {
            "schema_version": "musio.music_gene.v1",
            "generated_from": [
                "qq_music_gene_report",
                "liked_songs",
                "created_playlists",
                "favorite_playlists",
                "favorite_albums",
                "follow_singers",
            ],
            "summary": {
                "confidence": self._music_gene_confidence(source_signal_count),
                "top_artists": top_artists,
                "top_albums": top_albums,
                "top_genre_ids": self._rank_numeric_song_attr(liked_songs, "genre"),
                "top_language_ids": self._rank_numeric_song_attr(liked_songs, "language"),
                "liked_song_count": self._response_total(liked_result, len(liked_songs)),
                "created_playlist_count": self._created_songlist_total(created_result, len(created_playlists)),
                "favorite_playlist_count": self._response_total(favorite_songlist_result, len(favorite_playlists)),
                "favorite_album_count": self._response_total(favorite_album_result, len(favorite_albums)),
                "follow_singer_count": self._response_total(follow_singer_result, len(follow_singers)),
            },
            "signals": {
                "liked_songs": [
                    self._compact_song_signal(song)
                    for song in liked_songs[:_MUSIC_GENE_SIGNAL_LIMIT]
                ],
                "created_playlists": [
                    self._compact_playlist_signal(playlist, "created")
                    for playlist in created_playlists[:_MUSIC_GENE_LIST_LIMIT]
                ],
                "favorite_playlists": [
                    self._compact_playlist_signal(playlist, "favorite")
                    for playlist in favorite_playlists[:_MUSIC_GENE_LIST_LIMIT]
                ],
                "favorite_albums": [
                    self._compact_album_signal(album)
                    for album in favorite_albums[:_MUSIC_GENE_LIST_LIMIT]
                ],
                "follow_singers": [
                    self._compact_relation_user_signal(user)
                    for user in follow_singers[:_MUSIC_GENE_LIST_LIMIT]
                ],
            },
            "raw": raw,
            "errors": errors,
        }

    async def _optional_music_gene_source(
        self,
        source: str,
        errors: list[dict[str, str]],
        factory: Callable[[], Awaitable[Any]],
    ) -> Any | None:
        try:
            return await factory()
        except Exception as error:
            errors.append(
                {
                    "source": source,
                    "type": type(error).__name__,
                    "message": str(error)[:240],
                },
            )
            return None

    async def _raw_response(self, request: Any) -> dict[str, Any]:
        raw = await request.replace(response_model=None)
        return raw if isinstance(raw, dict) else {}

    async def _songlist_detail_response(
        self,
        client: Client,
        playlist_id: str,
        num: int,
        page: int = 1,
        *,
        onlysong: bool = False,
    ) -> dict[str, Any]:
        return await self._raw_response(
            client.songlist.get_detail(
                int(self._qqmusic_value(playlist_id)),
                num=num,
                page=page,
                onlysong=onlysong,
            ),
        )

    def _songlist_tracks(self, response: Any | None) -> list[Any]:
        return self._items_at(response, "songlist")

    async def _comment_response(self, request: Any) -> dict[str, Any]:
        return await self._raw_response(request)

    def _comment_items(self, response: Any | None, song_id: str, comment_type: str) -> list[Comment]:
        if comment_type == "moment":
            items = self._items_at(response, "CmList")
        else:
            items = self._items_at(response, "CommentList", "Comments")
        return [self._to_comment(item, song_id) for item in items]

    def _comment_has_more(self, response: Any | None, comment_type: str) -> bool:
        if comment_type == "moment":
            return bool(self._value(response, "HasMore", "has_more"))
        comment_list = self._value(response, "CommentList")
        return bool(self._value(comment_list, "HasMore", "has_more"))

    def _comment_next_cursor(self, response: Any | None, comments: list[Comment], comment_type: str) -> str:
        if comment_type == "moment":
            return self._text(self._value(response, "NextPos", "next_pos"))
        if not comments:
            return ""
        raw_comments = self._items_at(response, "CommentList", "Comments")
        if raw_comments:
            return self._text(self._value(raw_comments[-1], "SeqNo", "seq_no"))
        return comments[-1].id

    async def _created_songlist_response(self, client: Client, credential: Credential) -> dict[str, Any]:
        return await self._raw_response(client.user.get_created_songlist(credential.musicid, credential=credential))

    def _created_songlist_items(self, response: Any | None) -> list[Any]:
        if response is None:
            return []
        if isinstance(response, dict):
            for key in ("v_playlist", "playlists"):
                items = self._items_value(response.get(key))
                if items:
                    return items
            return []
        return list(getattr(response, "playlists", []) or [])

    def _created_songlist_total(self, response: Any | None, fallback: int) -> int:
        if isinstance(response, dict):
            value = response.get("total")
            try:
                total = int(value)
            except (TypeError, ValueError):
                return fallback
            return total if total >= 0 else fallback
        return self._response_total(response, fallback)

    def _recommend_playlist_items(self, response: Any | None) -> list[Any]:
        items = []
        for entry in self._items_at(response, "List"):
            playlist = self._value(entry, "Playlist") or entry
            basic = self._value(playlist, "basic") or playlist
            if isinstance(playlist, dict) and isinstance(basic, dict):
                items.append({**playlist, **basic})
            else:
                items.append(basic)
        return items

    def _compact_song_signal(self, song: Any) -> dict[str, Any]:
        data = self._jsonable(self._to_song(song))
        if isinstance(data, dict):
            return data
        return {"value": data}

    def _compact_playlist_signal(self, playlist: Any, source: str) -> dict[str, Any]:
        return self._compact_dict(
            {
                "id": f"{_SONG_ID_PREFIX}{self._playlist_identity(playlist)}",
                "name": self._text(self._value(playlist, "title", "name", "dissname", "dirName")),
                "source": source,
                "song_count": self._positive_int(
                    self._value(playlist, "songnum", "songNum", "song_cnt", "songCnt", "total_song_num"),
                ),
                "play_count": self._positive_int(
                    self._value(playlist, "play_cnt", "playCnt", "listennum"),
                ),
                "favorite_count": self._positive_int(self._value(playlist, "create_fav_cnt", "fav_cnt", "favCnt")),
                "comment_count": self._positive_int(self._value(playlist, "comment_cnt", "commentCnt")),
                "owner_name": self._text(
                    self._value(playlist, "nick", "nickname"),
                ),
                "artwork_url": self._text(self._value(playlist, "picurl", "picUrl", "cover", "logo")),
            },
        )

    def _compact_album_signal(self, album: Any) -> dict[str, Any]:
        name = self._album_name(album)
        return self._compact_dict(
            {
                "id": f"qqmusic:album:{self._value(album, 'mid', 'albumMid') or self._value(album, 'id', 'albumID') or ''}",
                "mid": self._text(self._value(album, "mid", "albumMid")),
                "name": name,
                "artists": self._album_artist_names(album),
                "song_count": self._positive_int(self._value(album, "songnum", "songNum", "totalNum")),
                "published_at": self._text(
                    self._value(album, "time_public", "publishDate", "pubtime"),
                ),
                "artwork_url": self._album_cover_url(album) or self._cover_url(album),
            },
        )

    def _compact_relation_user_signal(self, user: Any) -> dict[str, Any]:
        return self._compact_dict(
            {
                "id": self._text(self._value(user, "mid", "MID", "enc_uin", "EncUin")),
                "name": self._text(self._value(user, "name", "Name")),
                "description": self._text(self._value(user, "desc", "Desc")),
                "avatar_url": self._text(self._value(user, "avatar_url", "AvatarUrl")),
                "fan_count": self._positive_int(self._value(user, "fan_num", "FanNum")),
            },
        )

    def _rank_top_artists(
        self,
        songs: list[Any],
        albums: list[Any],
        follow_singers: list[Any],
    ) -> list[dict[str, Any]]:
        scores: Counter[str] = Counter()
        evidence_counts: Counter[str] = Counter()

        for song in songs:
            for name in self._song_artist_names(song):
                scores[name] += 3
                evidence_counts[name] += 1
        for album in albums:
            for name in self._album_artist_names(album):
                scores[name] += 2
                evidence_counts[name] += 1
        for user in follow_singers:
            name = self._text(getattr(user, "name", ""))
            if name:
                scores[name] += 4
                evidence_counts[name] += 1

        return [
            {
                "name": name,
                "score": int(score),
                "evidence_count": int(evidence_counts[name]),
            }
            for name, score in scores.most_common(20)
        ]

    def _rank_top_albums(self, songs: list[Any], albums: list[Any]) -> list[dict[str, Any]]:
        scores: Counter[str] = Counter()
        metadata: dict[str, dict[str, Any]] = {}

        for album in albums:
            name = self._album_name(album)
            if not name:
                continue
            scores[name] += 3
            metadata.setdefault(
                name,
                {
                    "name": name,
                    "artists": self._album_artist_names(album),
                    "artwork_url": self._album_cover_url(album) or self._cover_url(album),
                },
            )

        for song in songs:
            album = self._value(self._song_payload(song), "album")
            name = self._album_name(album)
            if not name:
                continue
            scores[name] += 1
            metadata.setdefault(
                name,
                {
                    "name": name,
                    "artists": self._album_artist_names(album),
                    "artwork_url": self._album_cover_url(album) or self._cover_url(album),
                },
            )

        return [
            {
                **metadata[name],
                "score": int(score),
            }
            for name, score in scores.most_common(20)
        ]

    def _rank_numeric_song_attr(self, songs: list[Any], attr: str) -> list[dict[str, int]]:
        counts: Counter[int] = Counter()
        for song in songs:
            value = self._value(self._song_payload(song), attr)
            number = self._positive_int(value)
            if number:
                counts[number] += 1
        return [{"id": int(value), "count": int(count)} for value, count in counts.most_common(15)]

    def _song_artist_names(self, song: Any) -> list[str]:
        song = self._song_payload(song)
        return [
            name if name else self._text(artist)
            for artist in self._items_value(self._value(song, "singer", "singers", "singerList") or [])
            if (name := self._text(self._value(artist, "name", "singerName"))) or isinstance(artist, str)
        ]

    def _album_artist_names(self, album: Any) -> list[str]:
        singer_container = self._value(album, "singer")
        artists = (
            self._value(album, "singers", "singer_list", "singerList", "v_singer")
            or self._value(singer_container, "singerList")
        )
        if artists:
            return [
                name
                for artist in self._items_value(artists)
                if (name := self._text(self._value(artist, "name", "singerName")))
            ]
        singer_name = self._text(self._value(album, "singer_name", "singerName", "singer"))
        if singer_name:
            return [singer_name]
        return []

    def _album_name(self, album: Any) -> str:
        if album is None:
            return ""
        if isinstance(album, str):
            return self._text(album)
        return self._text(self._value(album, "name", "title", "albumName"))

    def _cover_url(self, value: Any) -> str | None:
        if value is None:
            return None
        for attr in (
            "pic",
            "picurl",
            "picUrl",
            "singer_pic",
            "singerPic",
            "avatar_url",
            "AvatarUrl",
            "front_pic_url",
            "frontPicUrl",
            "head_pic_url",
            "headPicUrl",
            "bigpicUrl",
            "albumPicUrl",
        ):
            direct_url = self._direct_image_url(self._value(value, attr))
            if direct_url:
                return direct_url
        cover = self._value(value, "cover")
        cover_url = self._direct_image_url(self._value(cover, "default_url", "url"))
        if cover_url:
            return cover_url
        album = self._value(value, "album")
        if album is not None and album is not value:
            album_cover = self._album_cover_url(album)
            if album_cover:
                return album_cover
        for singer in self._items_value(self._value(value, "singer", "singers", "singerList") or []):
            singer_cover = self._singer_cover_url(singer)
            if singer_cover:
                return singer_cover
        album_cover = self._album_cover_url(value, allow_plain_mid=False)
        if album_cover:
            return album_cover
        singer_cover = self._singer_cover_url(value, allow_plain_mid=False)
        if singer_cover:
            return singer_cover
        if hasattr(value, "cover_url"):
            try:
                return value.cover_url() or None
            except Exception:
                return None
        return None

    def _album_cover_url(self, album: Any, *, allow_plain_mid: bool = True) -> str:
        direct_url = self._direct_image_url(
            self._value(album, "pic", "picurl", "picUrl", "cover", "front_pic_url", "frontPicUrl", "albumPicUrl", "bigpicUrl"),
        )
        if direct_url:
            return direct_url
        names = ["albumMid", "albumMID", "albumMId", "albummid", "pmid", "logo"]
        if allow_plain_mid:
            names.insert(0, "mid")
        mid = self._cover_mid(album, *names)
        return self._photo_new_cover_url("T002", mid) if mid else ""

    def _singer_cover_url(self, singer: Any, *, allow_plain_mid: bool = True) -> str:
        direct_url = self._direct_image_url(
            self._value(singer, "pic", "singer_pic", "singerPic", "SingerPic", "avatar_url", "AvatarUrl"),
        )
        if direct_url:
            return direct_url
        names = ["singerMid", "SingerMid", "singerMID", "singer_mid", "singerPmid", "SingerPMid", "pic_mid"]
        if allow_plain_mid:
            names.extend(("mid", "pmid"))
        mid = self._cover_mid(singer, *names)
        return self._photo_new_cover_url("T001", mid) if mid else ""

    def _direct_image_url(self, value: Any) -> str:
        text = self._text(value)
        if text.startswith("//"):
            return f"https:{text}"
        if text.startswith("http://") or text.startswith("https://"):
            return text
        return ""

    def _cover_mid(self, value: Any, *names: str) -> str:
        mid = self._text(self._value(value, *names))
        if not mid or mid.startswith("http://") or mid.startswith("https://") or mid.startswith("//"):
            return ""
        return mid

    def _photo_new_cover_url(self, kind: str, mid: str) -> str:
        normalized_mid = mid.strip()
        if not normalized_mid:
            return ""
        return f"{_PHOTO_NEW_DOMAIN}{kind}{_PHOTO_NEW_SIZE_SEGMENT}M000{normalized_mid}.jpg"

    def _response_total(self, response: Any | None, fallback: int) -> int:
        if response is None:
            return fallback
        value = self._value(response, "total", "Total", "totalNum", "total_song_num")
        try:
            total = int(value)
        except (TypeError, ValueError):
            return fallback
        if total >= 0:
            return total
        return fallback

    def _music_gene_confidence(self, source_signal_count: int) -> str:
        if source_signal_count >= 50:
            return "high"
        if source_signal_count >= 10:
            return "medium"
        return "low"

    def _compact_dict(self, data: dict[str, Any]) -> dict[str, Any]:
        return {
            key: value
            for key, value in data.items()
            if value is not None and value != "" and value != [] and value != {}
        }

    def _positive_int(self, value: Any) -> int | None:
        if isinstance(value, bool):
            return None
        try:
            number = int(value)
        except (TypeError, ValueError):
            return None
        return number if number > 0 else None

    def _text(self, value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    def _value(self, value: Any, *names: str) -> Any:
        if isinstance(value, dict):
            for name in names:
                if name in value and value[name] is not None:
                    return value[name]
            lowered = {str(key).lower(): item for key, item in value.items() if item is not None}
            for name in names:
                item = lowered.get(name.lower())
                if item is not None:
                    return item
            return None
        for name in names:
            item = getattr(value, name, None)
            if item is not None:
                return item
        return None

    def _items_at(self, value: Any | None, *path: str) -> list[Any]:
        if value is None:
            return []
        values = [value]
        for key in path:
            next_values = []
            for item in values:
                if key == "*":
                    next_values.extend(self._items_value(item))
                    continue
                for candidate in self._items_value(item) if isinstance(item, (list, tuple)) else [item]:
                    child = self._value(candidate, key)
                    next_values.extend(self._items_value(child))
            values = next_values
            if not values:
                return []
        return values

    def _items_value(self, value: Any) -> list[Any]:
        if value is None:
            return []
        if isinstance(value, list):
            return value
        if isinstance(value, tuple):
            return list(value)
        return [value]

    def _looks_like_playlist(self, value: dict[str, Any]) -> bool:
        playlist_keys = {"id", "tid", "dissid", "dirid", "dirId", "title", "name", "dissname", "dirName"}
        return any(key in value for key in playlist_keys)

    def _playlist_identity(self, playlist: Any) -> Any:
        playlist_id = self._value(playlist, "id", "tid", "dissid")
        if self._text(playlist_id) in {"", "-1", "0"}:
            playlist_id = self._value(playlist, "dirid", "dirId")
        return playlist_id or ""

    def _rank_hot_comments(self, comments: list[Comment]) -> list[Comment]:
        return sorted(
            (comment for comment in comments if self._is_user_hot_comment(comment)),
            key=lambda comment: self._positive_int(comment.liked_count) or 0,
            reverse=True,
        )

    def _is_user_hot_comment(self, comment: Comment) -> bool:
        author = self._text(comment.author_name)
        text = self._text(comment.text)
        if not text:
            return False
        if author in {"Q音辅导员", "QQ音乐小助手", "QQ音乐"}:
            return False
        normalized = text.replace(" ", "").replace("\u3000", "")
        blocked_fragments = (
            "@元宝介绍下这首歌",
            "元宝介绍下这首歌",
            "介绍下这首歌",
            "介绍一下这首歌",
        )
        return not any(fragment in normalized for fragment in blocked_fragments)

    async def _profile_from_credential(self, client: Client, credential: Credential) -> UserProfile:
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
        detail = await self._raw_response(client.song.get_detail(value))
        return self._value(detail, "track_info", "track") or detail

    def _song_file_info(self, track: Any) -> SongFileInfo:
        file_info = self._value(track, "file")
        return SongFileInfo(
            mid=self._text(self._value(track, "mid", "songmid", "songMid")),
            song_type=self._int_value(self._value(track, "type")),
            media_mid=self._text(self._value(file_info, "media_mid", "mediaMid")) or None,
        )

    async def _quality_playability(
        self,
        client: Client,
        file_info: SongFileInfo,
        file_type: SongFileType,
        track: Any,
    ) -> TrackPlayabilityQuality:
        try:
            response = await self._raw_response(client.song.get_song_urls([file_info], file_type=file_type))
            items = self._items_at(response, "midurlinfo")
            item = items[0] if items else None
            if item is None:
                return TrackPlayabilityQuality(
                    quality=file_type.name,
                    playable=False,
                    state=self._fallback_track_state(track, []),
                    message="No URL authorization result returned.",
                )
            state = self._url_result_state(self._int_value(self._value(item, "result")), self._text(self._value(item, "purl")))
            return TrackPlayabilityQuality(
                quality=file_type.name,
                playable=state == "PLAYABLE",
                state=state,
                message=self._quality_message(state, self._int_value(self._value(item, "result"))),
            )
        except Exception as error:
            return TrackPlayabilityQuality(
                quality=file_type.name,
                playable=False,
                state="UPSTREAM_ERROR",
                message=type(error).__name__,
            )

    def _url_result_state(self, result_code: int, purl: str) -> str:
        if result_code == 0 and purl:
            return "PLAYABLE"
        return {
            104003: "MEMBERSHIP_REQUIRED",
            104004: "UPSTREAM_ERROR",
            104013: "DEVICE_RESTRICTED",
        }.get(result_code, "UNKNOWN")

    def _quality_message(self, state: str, result_code: int) -> str:
        return {
            "PLAYABLE": "",
            "MEMBERSHIP_REQUIRED": "Membership or paid privilege is required.",
            "UPSTREAM_ERROR": "Provider failed to issue a playback key.",
            "DEVICE_RESTRICTED": "Playback is restricted on this device.",
            "UNKNOWN": f"Provider returned playback result code {result_code}.",
        }.get(state, state)

    def _fallback_track_state(self, track: Any, quality_results: list[TrackPlayabilityQuality]) -> str:
        states = [item.state for item in quality_results if item.state != "UNKNOWN"]
        if states:
            if "MEMBERSHIP_REQUIRED" in states:
                return "MEMBERSHIP_REQUIRED"
            if "DEVICE_RESTRICTED" in states:
                return "DEVICE_RESTRICTED"
            return states[0]

        status = self._int_value(self._value(track, "status"))
        if status == 3:
            return "REGION_RESTRICTED"
        if status not in (0, None):
            return "COPYRIGHT_RESTRICTED"

        pay = self._value(track, "pay")
        if pay is not None:
            if self._positive_int(self._value(pay, "price_track", "priceTrack")) or self._positive_int(
                self._value(pay, "price_album", "priceAlbum"),
            ):
                return "PURCHASE_REQUIRED"
            if self._positive_int(self._value(pay, "pay_play", "payPlay")) or self._positive_int(self._value(pay, "pay_month", "payMonth")):
                return "MEMBERSHIP_REQUIRED"
        return "UNKNOWN"

    def _fallback_track_reason(self, state: str, quality_results: list[TrackPlayabilityQuality]) -> str:
        for result in quality_results:
            if result.state == state and result.message:
                return result.message
        return {
            "REGION_RESTRICTED": "Track is restricted in the current region.",
            "COPYRIGHT_RESTRICTED": "Track is unavailable because of copyright or catalog status.",
            "PURCHASE_REQUIRED": "Track requires purchase.",
            "MEMBERSHIP_REQUIRED": "Track requires membership.",
            "UNKNOWN": "Provider did not return a playable URL.",
        }.get(state, state)

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

    def _typed_value(self, value: str, kind: str) -> str | int:
        typed_prefix = f"{_SONG_ID_PREFIX}{kind}:"
        raw = value.removeprefix(typed_prefix).removeprefix(_SONG_ID_PREFIX)
        return int(raw) if raw.isdigit() else raw

    def _chart_numeric_id(self, value: Any) -> int | None:
        if isinstance(value, int):
            return value if value > 0 else None
        raw = self._text(value)
        return int(raw) if raw.isdigit() else None

    def _chart_matches(self, chart: Any, requested: str) -> bool:
        if chart is None or not requested:
            return False
        for field in ("id", "topId", "name", "title", "title_detail", "titleDetail", "title_sub", "titleSub"):
            value = self._value(chart, field)
            if self._normalize_lookup_text(value) == requested:
                return True
        return False

    def _normalize_lookup_text(self, value: Any) -> str:
        text = re.sub(r"<[^>]+>", "", self._text(value))
        return re.sub(r"[\s\u3000]+", "", text).lower()

    def _song_payload(self, song: Any) -> Any:
        if isinstance(song, dict):
            for key in ("track", "Track", "songInfo", "song_info", "songinfo"):
                nested = song.get(key)
                if isinstance(nested, dict):
                    return nested
        return song

    def _to_song(self, song: Any) -> Song:
        song = self._song_payload(song)
        album = self._value(song, "album")
        return Song(
            id=self._song_identity(song),
            title=self._text(self._value(song, "title", "title_main", "name", "songName")),
            artists=self._song_artist_names(song),
            album=self._album_name(album) or None,
            duration_seconds=self._positive_int(self._value(song, "interval", "duration", "songInterval")),
            artwork_url=self._cover_url(song),
        )

    def _to_song_detail(self, song: Any) -> SongDetail:
        base = self._to_song(song)
        mid = self._text(self._value(self._song_payload(song), "mid", "songmid", "songMid"))
        return SongDetail(
            id=base.id,
            title=base.title,
            artists=base.artists,
            album=base.album,
            duration_seconds=base.duration_seconds,
            artwork_url=base.artwork_url,
            source_url=f"https://y.qq.com/n/ryqq/songDetail/{mid}" if mid else None,
        )

    def _to_comment(self, item: Any, song_id: str) -> Comment:
        return Comment(
            id=self._text(self._value(item, "cmid", "CmId")),
            song_id=song_id,
            author_name=self._text(self._value(item, "nick", "Nick")) or self._text(self._value(item, "encrypt_uin", "EncryptUin")),
            text=self._text(self._value(item, "content", "Content")),
            liked_count=self._positive_int(self._value(item, "praise_num", "PraiseNum")),
            created_at=self._timestamp_to_iso(self._value(item, "pub_time", "PubTime")),
        )

    def _to_playlist(self, playlist: Any) -> Playlist:
        return Playlist(
            id=f"{_SONG_ID_PREFIX}{self._playlist_identity(playlist)}",
            name=self._text(self._value(playlist, "title", "name", "dissname", "dirName")),
            song_count=self._positive_int(
                self._value(playlist, "songnum", "songNum", "song_cnt", "songCnt", "total_song_num"),
            ),
            artwork_url=self._text(self._value(playlist, "picurl", "picUrl", "cover", "logo")) or None,
        )

    def _to_playlist_detail(self, playlist: Any, total: int | None = None) -> dict[str, Any]:
        creator = self._value(playlist, "creator")
        return self._compact_dict(
            {
                "id": f"{_SONG_ID_PREFIX}{self._playlist_identity(playlist)}",
                "provider": "qqmusic",
                "name": self._text(self._value(playlist, "title", "name", "dirName")),
                "description": self._text(self._value(playlist, "desc", "description")),
                "songCount": self._positive_int(total)
                or self._positive_int(self._value(playlist, "songnum", "songNum", "total_song_num")),
                "artworkUrl": self._text(self._value(playlist, "picurl", "picUrl", "cover", "logo")),
                "ownerName": self._text(self._value(creator, "nick", "name")) if creator is not None else "",
                "ownerId": self._text(self._value(creator, "musicid", "uin")) if creator is not None else "",
                "playCount": self._positive_int(self._value(playlist, "listennum", "playCnt", "play_cnt")),
            },
        )

    def _to_album(self, album: Any) -> Album:
        album_id = self._text(self._value(album, "mid", "albumMid", "albumMId")) or self._text(
            self._value(album, "id", "albumID", "albumId"),
        )
        return Album(
            id=f"{_ALBUM_ID_PREFIX}{album_id}",
            title=self._album_name(album),
            artists=self._album_artist_names(album),
            artwork_url=self._album_cover_url(album) or self._cover_url(album),
            release_date=self._text(self._value(album, "time_public", "publishDate", "publish_date")) or None,
            song_count=self._positive_int(
                self._value(album, "songnum", "songNum", "total_num", "totalNum"),
            ),
            description=self._text(self._value(album, "description", "desc")) or None,
        )

    def _to_artist(self, artist: Any) -> Artist:
        artist_id = self._text(self._value(artist, "mid", "singerMid", "singerMID")) or self._text(
            self._value(artist, "id", "singerId", "singerID", "singer_id"),
        )
        aliases = [
            value
            for value in (
                self._text(self._value(artist, "other_name", "otherName")),
                self._text(self._value(artist, "subtitle")),
                self._text(self._value(artist, "foreign_name", "foreignName")),
            )
            if value
        ]
        genres = [
            value
            for value in (
                self._text(self._value(artist, "genre")),
                self._text(self._value(artist, "tag")),
            )
            if value
        ]
        return Artist(
            id=f"{_ARTIST_ID_PREFIX}{artist_id}",
            name=self._text(self._value(artist, "name", "singerName")) or self._text(self._value(artist, "title")),
            avatar_url=self._direct_image_url(self._value(artist, "pic", "singerPic", "singer_pic")) or self._singer_cover_url(artist),
            alias=aliases,
            genres=genres,
            song_count=self._positive_int(self._value(artist, "song_num", "songNum")),
            album_count=self._positive_int(self._value(artist, "album_num", "albumNum")),
        )

    def _to_chart(self, chart: Any) -> Chart:
        chart_id = self._text(self._value(chart, "id", "topId"))
        return Chart(
            id=f"{_CHART_ID_PREFIX}{chart_id}",
            name=self._text(self._value(chart, "name", "title")),
            description=self._text(self._value(chart, "intro")) or None,
            update_frequency=self._text(self._value(chart, "update_time", "updateTime"))
            or self._text(self._value(chart, "period"))
            or None,
            artwork_url=self._text(self._value(chart, "front_pic_url", "frontPicUrl"))
            or self._text(self._value(chart, "head_pic_url", "headPicUrl"))
            or None,
        )

    def _song_identity(self, song: Any) -> str:
        song = self._song_payload(song)
        return f"{_SONG_ID_PREFIX}{self._value(song, 'mid', 'songmid', 'songMid') or self._value(song, 'id', 'songId') or ''}"

    def _song_numeric_id(self, song: Any) -> int:
        song = self._song_payload(song)
        return self._int_value(self._value(song, "id", "songId"))

    def _track_web_url(self, song_id: str) -> str | None:
        value = self._typed_value(song_id, "")
        return f"https://y.qq.com/n/ryqq/songDetail/{value}" if value else None

    def _next_comment_cursor(self, result: Any) -> str:
        comments = getattr(result, "comments", []) or []
        if not comments:
            return ""
        return self._text(getattr(comments[-1], "seq_no", ""))

    def _strip_lrc_timestamps(self, text: str) -> str:
        lines = []
        for line in text.splitlines():
            cleaned = _LRC_TIMESTAMP_PATTERN.sub("", line).strip()
            if cleaned:
                lines.append(cleaned)
        return "\n".join(lines)

    def _timestamp_to_iso(self, value: Any) -> str | None:
        if not value:
            return None
        try:
            timestamp = int(value)
        except (TypeError, ValueError):
            return None
        return datetime.fromtimestamp(timestamp, UTC).isoformat().replace("+00:00", "Z")

    def _now_iso(self) -> str:
        return datetime.now(UTC).isoformat().replace("+00:00", "Z")

    def _jsonable(self, value: Any) -> Any:
        if hasattr(value, "model_dump"):
            return value.model_dump(mode="json", by_alias=True)
        if isinstance(value, dict):
            return {str(key): self._jsonable(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self._jsonable(item) for item in value]
        if isinstance(value, tuple):
            return [self._jsonable(item) for item in value]
        if isinstance(value, datetime):
            return value.isoformat()
        return value

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
