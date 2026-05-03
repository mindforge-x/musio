package com.musio.api;

import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import com.musio.providers.MusicProviderGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/music")
public class MusicController {
    private final MusicProviderGateway providerGateway;
    private final HttpClient httpClient;

    public MusicController(MusicProviderGateway providerGateway) {
        this.providerGateway = providerGateway;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @GetMapping("/profile")
    public UserProfile profile() {
        return providerGateway.defaultProvider().getProfile("local");
    }

    @GetMapping("/playlists")
    public List<Playlist> playlists() {
        return providerGateway.defaultProvider().getPlaylists("local");
    }

    @GetMapping("/playlists/{playlistId}/songs")
    public List<Song> playlistSongs(@PathVariable String playlistId) {
        return providerGateway.defaultProvider().getPlaylistSongs(playlistId);
    }

    @GetMapping("/search")
    public List<Song> search(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        return providerGateway.defaultProvider().searchSongs(keyword, limit);
    }

    @GetMapping("/songs/{songId}")
    public SongDetail song(@PathVariable String songId) {
        return providerGateway.defaultProvider().getSongDetail(songId);
    }

    @GetMapping("/songs/{songId}/url")
    public SongUrl songUrl(@PathVariable String songId) {
        return providerGateway.defaultProvider().getSongUrl(songId);
    }

    @GetMapping("/songs/{songId}/stream")
    public ResponseEntity<StreamingResponseBody> songStream(
            @PathVariable String songId,
            @RequestHeader(value = "Range", required = false) String range
    ) {
        SongUrl songUrl = providerGateway.defaultProvider().getSongUrl(songId);
        if (songUrl.url() == null || songUrl.url().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Song stream is not available.");
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(songUrl.url()))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .header("User-Agent", "Musio/0.1");
            if (range != null && !range.isBlank()) {
                requestBuilder.header("Range", range);
            }

            HttpResponse<InputStream> upstream = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (upstream.statusCode() >= 400) {
                closeQuietly(upstream.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider stream is not available.");
            }

            HttpHeaders headers = new HttpHeaders();
            copyHeader(upstream, headers, "content-type", HttpHeaders.CONTENT_TYPE);
            copyHeader(upstream, headers, "content-length", HttpHeaders.CONTENT_LENGTH);
            copyHeader(upstream, headers, "content-range", HttpHeaders.CONTENT_RANGE);
            copyHeader(upstream, headers, "accept-ranges", HttpHeaders.ACCEPT_RANGES);
            if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                headers.set(HttpHeaders.CONTENT_TYPE, "audio/mpeg");
            }

            StreamingResponseBody body = outputStream -> {
                try (InputStream inputStream = upstream.body()) {
                    inputStream.transferTo(outputStream);
                }
            };
            return ResponseEntity.status(upstream.statusCode()).headers(headers).body(body);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider returned an invalid stream URL.", error);
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to open provider stream.", error);
        }
    }

    @GetMapping("/songs/{songId}/lyrics")
    public Lyrics lyrics(@PathVariable String songId) {
        return providerGateway.defaultProvider().getLyrics(songId);
    }

    @GetMapping("/songs/{songId}/comments")
    public List<Comment> comments(@PathVariable String songId) {
        return providerGateway.defaultProvider().getComments(songId);
    }

    private static void copyHeader(HttpResponse<?> response, HttpHeaders headers, String upstreamName, String downstreamName) {
        Optional<String> value = response.headers().firstValue(upstreamName);
        value.ifPresent(header -> headers.set(downstreamName, header));
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (Exception ignored) {
            // Nothing useful to do while translating an upstream failure.
        }
    }
}
