from __future__ import annotations

from .schemas import Comment, Lyrics, Playlist, Song, SongDetail, SongUrl, UserProfile


class QQMusicClient:
    """Thin adapter around qqmusic-api-python.

    The real QQMusicApi integration should be added here so the Spring backend
    only talks to this sidecar through stable HTTP endpoints.
    """

    async def search(self, keyword: str, limit: int) -> list[Song]:
        return []

    async def song(self, song_id: str) -> SongDetail:
        return SongDetail(id=song_id, title="", artists=[])

    async def song_url(self, song_id: str) -> SongUrl:
        return SongUrl(song_id=song_id)

    async def lyrics(self, song_id: str) -> Lyrics:
        return Lyrics(song_id=song_id)

    async def comments(self, song_id: str) -> list[Comment]:
        return []

    async def profile(self) -> UserProfile:
        return UserProfile(id="local", display_name="Local QQ Music user")

    async def playlists(self) -> list[Playlist]:
        return []

    async def playlist_songs(self, playlist_id: str) -> list[Song]:
        return []
