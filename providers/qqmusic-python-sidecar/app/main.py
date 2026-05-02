from __future__ import annotations

import os

import uvicorn
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from qqmusic_api.core.exceptions import CredentialError, LoginExpiredError, NotLoginError, RatelimitedError

from .qqmusic_client import QQMusicClient
from .schemas import (
    Comment,
    HealthResponse,
    Lyrics,
    Playlist,
    Song,
    SongDetail,
    SongUrl,
    UserConnectionStatus,
    UserMusicGene,
    UserProfile,
)

app = FastAPI(title="Musio QQ Music Sidecar", version="0.1.0")
client = QQMusicClient()


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


if __name__ == "__main__":
    host = os.environ.get("MUSIO_QQMUSIC_HOST", "127.0.0.1")
    port = int(os.environ.get("MUSIO_QQMUSIC_PORT", "18767"))
    uvicorn.run("app.main:app", host=host, port=port, reload=False)
