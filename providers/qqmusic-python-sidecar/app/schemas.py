from __future__ import annotations

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str = "ok"
    provider: str = "qqmusic"


class Song(BaseModel):
    id: str
    title: str
    artists: list[str] = Field(default_factory=list)
    album: str | None = None
    duration_seconds: int | None = None
    artwork_url: str | None = None


class SongDetail(Song):
    source_url: str | None = None


class SongUrl(BaseModel):
    song_id: str
    url: str | None = None
    expires_in_seconds: int | None = None


class Lyrics(BaseModel):
    song_id: str
    plain_text: str = ""
    synced_text: str = ""


class Comment(BaseModel):
    id: str
    song_id: str
    author_name: str
    text: str
    liked_count: int | None = None
    created_at: str | None = None


class Playlist(BaseModel):
    id: str
    name: str
    song_count: int | None = None
    artwork_url: str | None = None


class UserProfile(BaseModel):
    id: str
    display_name: str
    avatar_url: str | None = None
