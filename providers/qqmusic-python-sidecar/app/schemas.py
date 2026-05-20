from __future__ import annotations

from typing import Any

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


class TrackPlayabilityQuality(BaseModel):
    quality: str
    playable: bool
    state: str
    message: str = ""


class TrackPlayability(BaseModel):
    provider: str = "qqmusic"
    track_id: str
    playable: bool
    state: str
    reason: str = ""
    best_quality: str | None = None
    quality_results: list[TrackPlayabilityQuality] = Field(default_factory=list)
    checked_at: str


class PlaybackResolution(BaseModel):
    provider: str = "qqmusic"
    track_id: str
    playback_mode: str
    stream_url: str | None = None
    provider_uri: str | None = None
    web_url: str | None = None
    expires_at: str | None = None
    expires_in_seconds: int | None = None
    state: str
    unsupported_reason: str | None = None


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


class Album(BaseModel):
    id: str
    provider: str = "qqmusic"
    title: str
    artists: list[str] = Field(default_factory=list)
    artwork_url: str | None = None
    release_date: str | None = None
    song_count: int | None = None
    description: str | None = None


class Artist(BaseModel):
    id: str
    provider: str = "qqmusic"
    name: str
    avatar_url: str | None = None
    alias: list[str] = Field(default_factory=list)
    genres: list[str] = Field(default_factory=list)
    description: str | None = None
    song_count: int | None = None
    album_count: int | None = None


class Chart(BaseModel):
    id: str
    provider: str = "qqmusic"
    name: str
    description: str | None = None
    update_frequency: str | None = None
    artwork_url: str | None = None


class UserProfile(BaseModel):
    id: str
    display_name: str
    avatar_url: str | None = None


class UserConnectionStatus(BaseModel):
    provider: str = "qqmusic"
    state: str
    credential_stored: bool
    authenticated: bool
    user_id: str | None = None
    display_name: str | None = None
    message: str
    checked_at: str


class UserMusicGene(BaseModel):
    provider: str = "qqmusic"
    user_id: str
    euin: str
    generated_at: str
    data: dict[str, Any] = Field(default_factory=dict)


class LoginStartResult(BaseModel):
    sessionId: str
    provider: str = "qqmusic"
    state: str
    qrCodeDataUrl: str | None = None
    message: str


class LoginStatus(BaseModel):
    sessionId: str
    provider: str = "qqmusic"
    state: str
    credentialStored: bool
    message: str


class SourceCapability(BaseModel):
    name: str
    tier: str = "P1"
    effect: str = "read"
    description: str = ""
    input_schema: dict[str, Any] = Field(default_factory=dict)
    required: list[str] = Field(default_factory=list)
    required_capability: bool = False
    enabled: bool = True
    disabled_reason: str | None = None
    result_type: str = "generic"


class SourceManifest(BaseModel):
    source_id: str = "qqmusic"
    display_name: str = "QQ 音乐"
    capabilities: list[SourceCapability] = Field(default_factory=list)
