package com.musio.agent.recommendation;

import com.musio.model.Song;
import com.musio.providers.MusicProviderGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class SongResolver {
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 10;

    private final MusicProviderGateway providerGateway;

    public SongResolver(MusicProviderGateway providerGateway) {
        this.providerGateway = providerGateway;
    }

    public RecommendationResult resolve(List<RecommendationCandidate> candidates, int requestedCount) {
        int limit = requestedCount(requestedCount);
        List<ResolvedRecommendation> resolved = new ArrayList<>();
        List<RecommendationCandidate> unresolved = new ArrayList<>();
        Set<String> resolvedSongIds = new LinkedHashSet<>();

        for (RecommendationCandidate candidate : candidates == null ? List.<RecommendationCandidate>of() : candidates) {
            if (resolved.size() >= limit) {
                break;
            }
            if (candidate == null || isBlank(candidate.title()) || isBlank(candidate.artist())) {
                continue;
            }
            Optional<Song> match = strictMatch(candidate);
            if (match.isPresent() && resolvedSongIds.add(match.get().id())) {
                resolved.add(new ResolvedRecommendation(match.get(), safe(candidate.reason()), query(candidate)));
            } else {
                unresolved.add(candidate);
            }
        }

        return new RecommendationResult(
                List.copyOf(resolved),
                List.copyOf(unresolved),
                "已精确匹配 " + resolved.size() + " 首歌曲，未匹配 " + unresolved.size() + " 首。"
        );
    }

    private Optional<Song> strictMatch(RecommendationCandidate candidate) {
        List<Song> songs;
        try {
            songs = providerGateway.defaultProvider().searchSongs(query(candidate), DEFAULT_SEARCH_LIMIT);
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return songs.stream()
                .filter(song -> titleMatches(candidate, song))
                .filter(song -> artistMatches(candidate, song))
                .findFirst();
    }

    private boolean titleMatches(RecommendationCandidate candidate, Song song) {
        return normalize(candidate.title()).equals(normalize(song.title()));
    }

    private boolean artistMatches(RecommendationCandidate candidate, Song song) {
        String expected = normalize(candidate.artist());
        if (expected.isBlank() || song.artists() == null || song.artists().isEmpty()) {
            return false;
        }
        return song.artists().stream()
                .map(this::normalize)
                .anyMatch(expected::equals);
    }

    private String query(RecommendationCandidate candidate) {
        return safe(candidate.title()) + " " + safe(candidate.artist());
    }

    private int requestedCount(int count) {
        int value = count <= 0 ? DEFAULT_RESULT_LIMIT : count;
        return Math.max(1, Math.min(MAX_RESULT_LIMIT, value));
    }

    private String normalize(String value) {
        StringBuilder builder = new StringBuilder();
        safe(value).toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
