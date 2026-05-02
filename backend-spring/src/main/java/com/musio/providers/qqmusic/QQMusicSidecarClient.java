package com.musio.providers.qqmusic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.config.MusioConfigService;
import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.Playlist;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class QQMusicSidecarClient {
    private final MusioConfigService configService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public QQMusicSidecarClient(MusioConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public UserProfile profile() {
        SidecarUserProfile profile = get("/users/me", SidecarUserProfile.class);
        return new UserProfile(profile.id(), ProviderType.QQMUSIC, profile.displayName(), profile.avatarUrl());
    }

    public QQMusicConnectionStatus connectionStatus() {
        SidecarConnectionStatus status = get("/users/me/status", SidecarConnectionStatus.class);
        return new QQMusicConnectionStatus(
                status.state(),
                status.credentialStored(),
                status.authenticated(),
                status.userId(),
                status.displayName(),
                status.message(),
                parseInstant(status.checkedAt())
        );
    }

    public MusicGeneSnapshot musicGene() {
        SidecarMusicGene musicGene = get("/users/me/music-gene", SidecarMusicGene.class);
        return new MusicGeneSnapshot(
                ProviderType.QQMUSIC,
                musicGene.userId(),
                musicGene.euin(),
                parseInstant(musicGene.generatedAt()),
                musicGene.data() == null ? Map.of() : musicGene.data()
        );
    }

    public List<Playlist> playlists() {
        return getList("/users/me/playlists", new TypeReference<List<SidecarPlaylist>>() {
        }).stream().map(this::toPlaylist).toList();
    }

    public List<Song> playlistSongs(String playlistId) {
        return getList("/playlists/" + playlistId + "/songs", new TypeReference<List<SidecarSong>>() {
        }).stream().map(this::toSong).toList();
    }

    public List<Song> search(String keyword, int limit) {
        HttpUrl url = baseHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("limit", Integer.toString(limit))
                .build();
        return getList(url, new TypeReference<List<SidecarSong>>() {
        }).stream().map(this::toSong).toList();
    }

    public SongDetail song(String songId) {
        return toSongDetail(get("/songs/" + songId, SidecarSongDetail.class));
    }

    public SongUrl songUrl(String songId) {
        SidecarSongUrl songUrl = get("/songs/" + songId + "/url", SidecarSongUrl.class);
        return new SongUrl(songUrl.songId(), ProviderType.QQMUSIC, songUrl.url(), songUrl.expiresInSeconds());
    }

    public Lyrics lyrics(String songId) {
        SidecarLyrics lyrics = get("/songs/" + songId + "/lyrics", SidecarLyrics.class);
        return new Lyrics(lyrics.songId(), ProviderType.QQMUSIC, lyrics.plainText(), lyrics.syncedText());
    }

    public List<Comment> comments(String songId) {
        return getList("/songs/" + songId + "/comments", new TypeReference<List<SidecarComment>>() {
        }).stream().map(this::toComment).toList();
    }

    private Song toSong(SidecarSong song) {
        return new Song(
                song.id(),
                ProviderType.QQMUSIC,
                song.title(),
                song.artists() == null ? List.of() : song.artists(),
                song.album(),
                song.durationSeconds(),
                song.artworkUrl()
        );
    }

    private SongDetail toSongDetail(SidecarSongDetail song) {
        return new SongDetail(
                song.id(),
                ProviderType.QQMUSIC,
                song.title(),
                song.artists() == null ? List.of() : song.artists(),
                song.album(),
                song.durationSeconds(),
                song.artworkUrl(),
                song.sourceUrl()
        );
    }

    private Playlist toPlaylist(SidecarPlaylist playlist) {
        return new Playlist(
                playlist.id(),
                ProviderType.QQMUSIC,
                playlist.name(),
                playlist.songCount(),
                playlist.artworkUrl()
        );
    }

    private Comment toComment(SidecarComment comment) {
        return new Comment(
                comment.id(),
                comment.songId(),
                ProviderType.QQMUSIC,
                comment.authorName(),
                comment.text(),
                comment.likedCount(),
                parseInstant(comment.createdAt())
        );
    }

    private <T> T get(String path, Class<T> responseType) {
        HttpUrl url = appendPath(path);
        try {
            return execute(url, body -> objectMapper.readValue(body.string(), responseType));
        } catch (IOException e) {
            throw new IllegalStateException("QQ Music sidecar request failed: " + url, e);
        }
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> responseType) {
        return getList(appendPath(path), responseType);
    }

    private <T> List<T> getList(HttpUrl url, TypeReference<List<T>> responseType) {
        try {
            return execute(url, body -> objectMapper.readValue(body.string(), responseType));
        } catch (IOException e) {
            throw new IllegalStateException("QQ Music sidecar request failed: " + url, e);
        }
    }

    private <T> T execute(HttpUrl url, BodyReader<T> reader) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw sidecarHttpException(response.code(), url, body);
            }
            return reader.read(body);
        }
    }

    private RuntimeException sidecarHttpException(int statusCode, HttpUrl url, ResponseBody body) throws IOException {
        String detail = sidecarErrorDetail(body);
        if (statusCode == 401) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, detail.isBlank() ? "QQ 音乐登录状态不可用，请重新登录。" : detail);
        }
        if (statusCode == 429) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, detail.isBlank() ? "QQ 音乐触发风控，请稍后再试。" : detail);
        }
        String message = "QQ Music sidecar returned HTTP " + statusCode + " for " + url;
        if (!detail.isBlank()) {
            message += ": " + detail;
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private String sidecarErrorDetail(ResponseBody body) throws IOException {
        if (body == null) {
            return "";
        }
        String value = body.string();
        if (value.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode detail = root.path("detail");
            return detail.isTextual() ? detail.asText().strip() : value.strip();
        } catch (Exception ignored) {
            return value.strip();
        }
    }

    private HttpUrl appendPath(String path) {
        HttpUrl.Builder builder = baseHttpUrl().newBuilder();
        for (String segment : path.split("/")) {
            if (!segment.isBlank()) {
                builder.addPathSegment(segment);
            }
        }
        return builder.build();
    }

    private HttpUrl baseHttpUrl() {
        HttpUrl url = HttpUrl.parse(configService.config().providers().qqmusic().sidecarBaseUrl());
        if (url == null) {
            throw new IllegalStateException("Invalid QQ Music sidecar URL.");
        }
        return url;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    @FunctionalInterface
    private interface BodyReader<T> {
        T read(ResponseBody body) throws IOException;
    }

    private record SidecarSong(
            String id,
            String title,
            List<String> artists,
            String album,
            @JsonProperty("duration_seconds") Integer durationSeconds,
            @JsonProperty("artwork_url") String artworkUrl
    ) {
    }

    private record SidecarSongDetail(
            String id,
            String title,
            List<String> artists,
            String album,
            @JsonProperty("duration_seconds") Integer durationSeconds,
            @JsonProperty("artwork_url") String artworkUrl,
            @JsonProperty("source_url") String sourceUrl
    ) {
    }

    private record SidecarSongUrl(
            @JsonProperty("song_id") String songId,
            String url,
            @JsonProperty("expires_in_seconds") Integer expiresInSeconds
    ) {
    }

    private record SidecarLyrics(
            @JsonProperty("song_id") String songId,
            @JsonProperty("plain_text") String plainText,
            @JsonProperty("synced_text") String syncedText
    ) {
    }

    private record SidecarComment(
            String id,
            @JsonProperty("song_id") String songId,
            @JsonProperty("author_name") String authorName,
            String text,
            @JsonProperty("liked_count") Integer likedCount,
            @JsonProperty("created_at") String createdAt
    ) {
    }

    private record SidecarPlaylist(
            String id,
            String name,
            @JsonProperty("song_count") Integer songCount,
            @JsonProperty("artwork_url") String artworkUrl
    ) {
    }

    private record SidecarUserProfile(
            String id,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("avatar_url") String avatarUrl
    ) {
    }

    private record SidecarConnectionStatus(
            String state,
            @JsonProperty("credential_stored") boolean credentialStored,
            boolean authenticated,
            @JsonProperty("user_id") String userId,
            @JsonProperty("display_name") String displayName,
            String message,
            @JsonProperty("checked_at") String checkedAt
    ) {
    }

    private record SidecarMusicGene(
            @JsonProperty("user_id") String userId,
            String euin,
            @JsonProperty("generated_at") String generatedAt,
            Map<String, Object> data
    ) {
    }

    public record QQMusicConnectionStatus(
            String state,
            boolean credentialStored,
            boolean authenticated,
            String userId,
            String displayName,
            String message,
            Instant checkedAt
    ) {
    }
}
