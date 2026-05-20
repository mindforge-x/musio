from __future__ import annotations

import os
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from qqmusic_api.core.exceptions import CredentialError, LoginExpiredError, NotLoginError, RatelimitedError

from .qqmusic_auth import QQMusicAuthService
from .qqmusic_client import QQMusicClient
from .schemas import (
    Comment,
    HealthResponse,
    LoginStartResult,
    LoginStatus,
    Lyrics,
    Playlist,
    Song,
    SongDetail,
    SongUrl,
    SourceCapability,
    SourceManifest,
    UserConnectionStatus,
    UserMusicGene,
    UserProfile,
)

app = FastAPI(title="Musio QQ Music Sidecar", version="0.1.0")
client = QQMusicClient()
auth_service = QQMusicAuthService()

_SOURCE_ID = "qqmusic"
_CAPABILITIES = [
    SourceCapability(
        name="get_source_status",
        tier="P0",
        description="读取音乐源连接状态和账号状态",
        input_schema={},
        required=[],
        required_capability=True,
        result_type="source_status",
    ),
    SourceCapability(
        name="search_tracks",
        tier="P0",
        description="按关键词搜索歌曲",
        input_schema={"keyword": "string", "limit": "number", "excludedTitles": "string[]"},
        required=["keyword", "limit"],
        required_capability=True,
        result_type="tracks",
    ),
    SourceCapability(
        name="get_track_detail",
        tier="P0",
        description="读取歌曲标准详情",
        input_schema={"trackId": "string"},
        required=["trackId"],
        required_capability=True,
        result_type="track_detail",
    ),
    SourceCapability(
        name="get_track_playability",
        tier="P0",
        description="判断歌曲是否可播放并返回不可播放原因",
        input_schema={"trackId": "string"},
        required=["trackId"],
        required_capability=True,
        result_type="track_playability",
    ),
    SourceCapability(
        name="resolve_playback",
        tier="P0",
        description="解析歌曲播放入口，可返回 streamUrl、providerUri 或 webUrl",
        input_schema={"trackId": "string"},
        required=["trackId"],
        required_capability=True,
        result_type="playback_resolution",
    ),
    SourceCapability(
        name="get_lyrics",
        tier="P1",
        description="读取歌曲歌词",
        input_schema={"trackId": "string", "songId": "string"},
        required=[],
        result_type="lyrics",
    ),
    SourceCapability(
        name="get_user_playlists",
        tier="P1",
        description="读取当前用户歌单",
        input_schema={"limit": "number"},
        required=[],
        result_type="playlists",
    ),
    SourceCapability(
        name="get_playlist_detail",
        tier="P1",
        description="读取歌单元信息",
        input_schema={"playlistId": "string"},
        required=["playlistId"],
        result_type="playlist_detail",
    ),
    SourceCapability(
        name="get_playlist_tracks",
        tier="P1",
        description="读取歌单歌曲分页",
        input_schema={"playlistId": "string", "limit": "number", "page": "number"},
        required=["playlistId"],
        result_type="tracks",
    ),
    SourceCapability(
        name="search_artists",
        tier="P1",
        description="按关键词搜索歌手",
        input_schema={"keyword": "string", "limit": "number", "page": "number"},
        required=["keyword"],
        result_type="artists",
    ),
    SourceCapability(
        name="get_artist_detail",
        tier="P1",
        description="读取歌手详情",
        input_schema={"artistId": "string"},
        required=["artistId"],
        result_type="artist_detail",
    ),
    SourceCapability(
        name="get_artist_tracks",
        tier="P1",
        description="读取歌手歌曲分页",
        input_schema={"artistId": "string", "limit": "number", "page": "number"},
        required=["artistId"],
        result_type="tracks",
    ),
    SourceCapability(
        name="search_albums",
        tier="P1",
        description="按关键词搜索专辑",
        input_schema={"keyword": "string", "limit": "number", "page": "number"},
        required=["keyword"],
        result_type="albums",
    ),
    SourceCapability(
        name="get_album_detail",
        tier="P1",
        description="读取专辑详情",
        input_schema={"albumId": "string"},
        required=["albumId"],
        result_type="album_detail",
    ),
    SourceCapability(
        name="get_album_tracks",
        tier="P1",
        description="读取专辑歌曲分页",
        input_schema={"albumId": "string", "limit": "number", "page": "number"},
        required=["albumId"],
        result_type="tracks",
    ),
    SourceCapability(
        name="search_playlists",
        tier="P1",
        description="按关键词搜索歌单",
        input_schema={"keyword": "string", "limit": "number", "page": "number"},
        required=["keyword"],
        result_type="playlists",
    ),
    SourceCapability(
        name="get_track_comments",
        tier="P2",
        description="读取歌曲评论分页，支持 hot/new/recommend/moment",
        input_schema={"trackId": "string", "songId": "string", "type": "string", "limit": "number", "page": "number", "cursor": "string"},
        required=[],
        result_type="comments",
    ),
    SourceCapability(
        name="get_recommend_tracks",
        tier="P2",
        description="读取音乐源推荐歌曲",
        input_schema={"type": "string", "limit": "number", "page": "number"},
        required=[],
        result_type="tracks",
    ),
    SourceCapability(
        name="get_recommend_playlists",
        tier="P2",
        description="读取音乐源推荐歌单",
        input_schema={"limit": "number", "page": "number"},
        required=[],
        result_type="playlists",
    ),
    SourceCapability(
        name="get_chart_categories",
        tier="P2",
        description="读取排行榜分类",
        input_schema={},
        required=[],
        result_type="chart_categories",
    ),
    SourceCapability(
        name="get_chart_detail",
        tier="P2",
        description="读取排行榜详情和歌曲",
        input_schema={"chartId": "string", "limit": "number", "page": "number"},
        required=["chartId"],
        result_type="chart_detail",
    ),
    SourceCapability(
        name="create_provider_playlist",
        tier="P2",
        effect="account_write",
        description="创建远端 QQ 音乐歌单；当前只预留契约，不自动执行",
        input_schema={"name": "string", "description": "string"},
        required=["name"],
        required_capability=False,
        enabled=False,
        disabled_reason="remote playlist write contract is reserved; execution is gated by ACCOUNT_WRITE confirmation",
        result_type="playlist_write_result",
    ),
    SourceCapability(
        name="add_tracks_to_provider_playlist",
        tier="P2",
        effect="account_write",
        description="添加歌曲到远端 QQ 音乐歌单；当前只预留契约，不自动执行",
        input_schema={"playlistId": "string", "trackIds": "string[]"},
        required=["playlistId", "trackIds"],
        required_capability=False,
        enabled=False,
        disabled_reason="remote playlist write contract is reserved; execution is gated by ACCOUNT_WRITE confirmation",
        result_type="playlist_write_result",
    ),
    SourceCapability(
        name="remove_tracks_from_provider_playlist",
        tier="P2",
        effect="account_write",
        description="从远端 QQ 音乐歌单移除歌曲；当前只预留契约，不自动执行",
        input_schema={"playlistId": "string", "trackIds": "string[]"},
        required=["playlistId", "trackIds"],
        required_capability=False,
        enabled=False,
        disabled_reason="remote playlist write contract is reserved; execution is gated by ACCOUNT_WRITE confirmation",
        result_type="playlist_write_result",
    ),
    SourceCapability(
        name="delete_provider_playlist",
        tier="P2",
        effect="account_write",
        description="删除远端 QQ 音乐歌单；当前只预留契约，不自动执行",
        input_schema={"playlistId": "string"},
        required=["playlistId"],
        required_capability=False,
        enabled=False,
        disabled_reason="remote playlist write contract is reserved; execution is gated by ACCOUNT_WRITE confirmation",
        result_type="playlist_write_result",
    ),
    SourceCapability(
        name="search_songs",
        tier="LEGACY",
        description="兼容旧 Agent 工具名：按关键词搜索歌曲",
        input_schema={"keyword": "string", "limit": "number", "excludedTitles": "string[]"},
        required=["keyword", "limit"],
        result_type="songs",
    ),
    SourceCapability(
        name="get_song_detail",
        tier="LEGACY",
        description="兼容旧 Agent 工具名：读取歌曲详情",
        input_schema={"songId": "string"},
        required=["songId"],
        result_type="song_detail",
    ),
    SourceCapability(
        name="get_song_url",
        tier="LEGACY",
        description="兼容旧播放器工具名：读取歌曲播放地址",
        input_schema={"songId": "string"},
        required=["songId"],
        result_type="song_url",
    ),
    SourceCapability(
        name="get_hot_comments",
        tier="LEGACY",
        description="兼容旧 Agent 工具名：读取歌曲热门评论",
        input_schema={"songId": "string", "limit": "number"},
        required=["songId"],
        result_type="comments",
    ),
    SourceCapability(
        name="get_playlist_songs",
        tier="LEGACY",
        description="兼容旧 Agent 工具名：读取歌单歌曲",
        input_schema={"playlistId": "string", "limit": "number"},
        required=["playlistId"],
        result_type="songs",
    ),
]


@app.exception_handler(LoginExpiredError)
async def login_expired_handler(_request, _error: LoginExpiredError) -> JSONResponse:
    return JSONResponse(status_code=401, content={"detail": "QQ 音乐登录已过期，请重新登录。"})


@app.exception_handler(NotLoginError)
async def not_login_handler(_request, _error: NotLoginError) -> JSONResponse:
    return JSONResponse(status_code=401, content={"detail": "QQ 音乐尚未登录，请先登录。"})


@app.exception_handler(CredentialError)
async def credential_error_handler(_request, _error: CredentialError) -> JSONResponse:
    return JSONResponse(status_code=401, content={"detail": "QQ 音乐登录状态不可用，请重新登录。"})


@app.exception_handler(RatelimitedError)
async def ratelimited_handler(_request, _error: RatelimitedError) -> JSONResponse:
    return JSONResponse(status_code=429, content={"detail": "QQ 音乐触发风控，需要登录或完成安全验证后再试。"})


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse()


@app.get("/manifest", response_model=SourceManifest)
async def manifest() -> SourceManifest:
    return SourceManifest(capabilities=_CAPABILITIES)


@app.get("/status", response_model=UserConnectionStatus)
async def status() -> UserConnectionStatus:
    return await client.connection_status()


@app.post("/tools/{tool_name}")
async def execute_tool(tool_name: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    arguments = _tool_arguments(payload)
    match tool_name:
        case "get_source_status":
            return _tool_result(tool_name, "source_status", status=await client.connection_status())
        case "search_tracks":
            keyword = _required_text(arguments, "keyword")
            limit = _int_arg(arguments, "limit", 5, 1, 50)
            tracks = await client.search(keyword, limit)
            return _track_result(tool_name, "tracks", tracks)
        case "get_track_detail":
            track_detail = await client.song(_required_track_id(arguments))
            return _tool_result(tool_name, "track_detail", track=track_detail, song=track_detail)
        case "get_track_playability":
            playability = await client.track_playability(_required_track_id(arguments))
            return _tool_result(tool_name, "track_playability", playability=playability)
        case "resolve_playback":
            playback = await client.resolve_playback(_required_track_id(arguments))
            return _tool_result(tool_name, "playback_resolution", playback=playback)
        case "search_songs":
            keyword = _required_text(arguments, "keyword")
            limit = _int_arg(arguments, "limit", 5, 1, 50)
            songs = await client.search(keyword, limit)
            return _tool_result(tool_name, "songs", count=len(songs), songs=songs, tracks=songs)
        case "get_song_detail":
            song_detail = await client.song(_required_text(arguments, "songId"))
            return _tool_result(tool_name, "song_detail", song=song_detail, track=song_detail)
        case "get_song_url":
            song_url = await client.song_url(_required_text(arguments, "songId"))
            return _tool_result(tool_name, "song_url", songUrl=song_url)
        case "get_lyrics":
            song_lyrics = await client.lyrics(_required_track_id(arguments))
            return _tool_result(tool_name, "lyrics", lyrics=song_lyrics)
        case "get_hot_comments":
            limit = _int_arg(arguments, "limit", 10, 1, 30)
            comments = (await client.comments(_required_text(arguments, "songId")))[:limit]
            return _tool_result(tool_name, "comments", count=len(comments), comments=comments)
        case "get_track_comments":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            comment_type = _text_arg(arguments, "type", "hot")
            cursor = _text_arg(arguments, "cursor", "")
            result = await client.track_comments(_required_track_id(arguments), comment_type, limit, page, cursor)
            return _tool_result(tool_name, "comments", **result)
        case "get_user_playlists":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            playlists = (await client.playlists())[:limit]
            return _tool_result(tool_name, "playlists", count=len(playlists), playlists=playlists)
        case "get_playlist_detail":
            playlist = await client.playlist_detail(_required_text(arguments, "playlistId"))
            return _tool_result(tool_name, "playlist_detail", playlist=playlist)
        case "get_playlist_songs":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            songs = (await client.playlist_songs(_required_text(arguments, "playlistId")))[:limit]
            return _tool_result(tool_name, "songs", count=len(songs), songs=songs, tracks=songs)
        case "get_playlist_tracks":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            tracks = await client.playlist_tracks(_required_text(arguments, "playlistId"), limit, page)
            return _track_result(tool_name, "tracks", tracks)
        case "search_artists":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            artists = await client.search_artists(_required_text(arguments, "keyword"), limit, page)
            return _tool_result(tool_name, "artists", count=len(artists), artists=artists)
        case "get_artist_detail":
            artist = await client.artist_detail(_required_text(arguments, "artistId"))
            return _tool_result(tool_name, "artist_detail", artist=artist)
        case "get_artist_tracks":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            tracks = await client.artist_tracks(_required_text(arguments, "artistId"), limit, page)
            return _track_result(tool_name, "tracks", tracks)
        case "search_albums":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            albums = await client.search_albums(_required_text(arguments, "keyword"), limit, page)
            return _tool_result(tool_name, "albums", count=len(albums), albums=albums)
        case "get_album_detail":
            album = await client.album_detail(_required_text(arguments, "albumId"))
            return _tool_result(tool_name, "album_detail", album=album)
        case "get_album_tracks":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            tracks = await client.album_tracks(_required_text(arguments, "albumId"), limit, page)
            return _track_result(tool_name, "tracks", tracks)
        case "search_playlists":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            playlists = await client.search_playlists(_required_text(arguments, "keyword"), limit, page)
            return _tool_result(tool_name, "playlists", count=len(playlists), playlists=playlists)
        case "get_recommend_tracks":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            recommend_type = _text_arg(arguments, "type", "guess")
            tracks = await client.recommend_tracks(recommend_type, limit, page)
            return _track_result(tool_name, "tracks", tracks)
        case "get_recommend_playlists":
            limit = _int_arg(arguments, "limit", 10, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            playlists = await client.recommend_playlists(limit, page)
            return _tool_result(tool_name, "playlists", count=len(playlists), playlists=playlists)
        case "get_chart_categories":
            result = await client.chart_categories()
            return _tool_result(tool_name, "chart_categories", **result)
        case "get_chart_detail":
            limit = _int_arg(arguments, "limit", 20, 1, 50)
            page = _int_arg(arguments, "page", 1, 1, 100)
            result = await client.chart_detail(_required_text(arguments, "chartId"), limit, page)
            return _tool_result(tool_name, "chart_detail", **result)
        case "create_provider_playlist" | "add_tracks_to_provider_playlist" | "remove_tracks_from_provider_playlist" | "delete_provider_playlist":
            raise HTTPException(status_code=403, detail="ACCOUNT_WRITE capability is reserved and requires explicit confirmation before implementation.")
        case _:
            raise HTTPException(status_code=404, detail=f"Unknown QQ Music tool: {tool_name}")


@app.post("/auth/start", response_model=LoginStartResult)
async def auth_start() -> LoginStartResult:
    return auth_service.start_login()


@app.get("/auth/{session_id}/status", response_model=LoginStatus)
async def auth_status(session_id: str) -> LoginStatus:
    return auth_service.check_login(session_id)


@app.post("/auth/logout", response_model=LoginStatus)
async def auth_logout() -> LoginStatus:
    return auth_service.logout()


@app.get("/search", response_model=list[Song])
async def search(keyword: str, limit: int = Query(default=10, ge=1, le=50)) -> list[Song]:
    return await client.search(keyword, limit)


@app.get("/songs/{song_id}", response_model=SongDetail)
async def song(song_id: str) -> SongDetail:
    return await client.song(song_id)


@app.get("/songs/{song_id}/url", response_model=SongUrl)
async def song_url(song_id: str) -> SongUrl:
    return await client.song_url(song_id)


@app.get("/songs/{song_id}/lyrics", response_model=Lyrics)
async def lyrics(song_id: str) -> Lyrics:
    return await client.lyrics(song_id)


@app.get("/songs/{song_id}/comments", response_model=list[Comment])
async def comments(song_id: str) -> list[Comment]:
    return await client.comments(song_id)


@app.get("/users/me", response_model=UserProfile)
async def profile() -> UserProfile:
    return await client.profile()


@app.get("/users/me/status", response_model=UserConnectionStatus)
async def profile_status() -> UserConnectionStatus:
    return await client.connection_status()


@app.get("/users/me/music-gene", response_model=UserMusicGene)
async def music_gene() -> UserMusicGene:
    try:
        return await client.music_gene()
    except PermissionError as error:
        raise HTTPException(status_code=401, detail=str(error)) from error


@app.get("/users/me/playlists", response_model=list[Playlist])
async def playlists() -> list[Playlist]:
    return await client.playlists()


@app.get("/playlists/{playlist_id}/songs", response_model=list[Song])
async def playlist_songs(playlist_id: str) -> list[Song]:
    return await client.playlist_songs(playlist_id)


def _tool_arguments(payload: dict[str, Any] | None) -> dict[str, Any]:
    if not payload:
        return {}
    arguments = payload.get("arguments")
    if isinstance(arguments, dict):
        return arguments
    return payload


def _required_text(arguments: dict[str, Any], key: str) -> str:
    value = arguments.get(key)
    if not isinstance(value, str) or not value.strip():
        raise HTTPException(status_code=400, detail=f"Missing required argument: {key}")
    return value.strip()


def _required_track_id(arguments: dict[str, Any]) -> str:
    for key in ("trackId", "songId"):
        value = arguments.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    raise HTTPException(status_code=400, detail="Missing required argument: trackId")


def _int_arg(arguments: dict[str, Any], key: str, default: int, min_value: int, max_value: int) -> int:
    value = arguments.get(key, default)
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = default
    return max(min_value, min(max_value, parsed))


def _text_arg(arguments: dict[str, Any], key: str, default: str) -> str:
    value = arguments.get(key, default)
    return value.strip() if isinstance(value, str) and value.strip() else default


def _track_result(tool_name: str, result_type: str, tracks: list[Any]) -> dict[str, Any]:
    return _tool_result(tool_name, result_type, count=len(tracks), tracks=tracks, songs=tracks)


def _tool_result(tool_name: str, result_type: str, **values: Any) -> dict[str, Any]:
    return {
        "success": True,
        "sourceId": _SOURCE_ID,
        "toolName": tool_name,
        "resultType": result_type,
        **values,
    }


if __name__ == "__main__":
    host = os.environ.get("MUSIO_QQMUSIC_HOST", "127.0.0.1")
    port = int(os.environ.get("MUSIO_QQMUSIC_PORT", "18767"))
    uvicorn.run("app.main:app", host=host, port=port, reload=False)
