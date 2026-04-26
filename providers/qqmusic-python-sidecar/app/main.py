from __future__ import annotations

import os

import uvicorn
from fastapi import FastAPI, Query

from .qqmusic_client import QQMusicClient
from .schemas import Comment, HealthResponse, Lyrics, Playlist, Song, SongDetail, SongUrl, UserProfile

app = FastAPI(title="Musio QQ Music Sidecar", version="0.1.0")
client = QQMusicClient()


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
