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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SongResolver {
    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 10;
    private static final Pattern TRAILING_BRACKET_QUALIFIER = Pattern.compile("\\s*[（(\\[【]([^）)\\]】]+)[）)\\]】]\\s*$");
    private static final List<String> SAFE_TITLE_SUFFIXES = List.of(
            "liveversion",
            "live",
            "现场版",
            "现场",
            "专辑版",
            "完整版",
            "录音室版",
            "albumversion",
            "radioversion",
            "radioedit"
    );
    private static final List<String> UNSAFE_TITLE_QUALIFIERS = List.of(
            "dj",
            "remix",
            "cover",
            "翻唱",
            "伴奏",
            "纯音乐",
            "karaoke"
    );

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
                "已可信匹配 " + resolved.size() + " 首歌曲，未匹配 " + unresolved.size() + " 首。"
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
        Set<String> expected = titleVariants(candidate.title());
        Set<String> actual = titleVariants(song.title());
        return expected.stream().anyMatch(actual::contains);
    }

    private boolean artistMatches(RecommendationCandidate candidate, Song song) {
        String expected = normalize(candidate.artist());
        if (expected.isBlank() || song.artists() == null || song.artists().isEmpty()) {
            return false;
        }
        return song.artists().stream()
                .map(this::normalize)
                .anyMatch(actual -> artistEquivalent(expected, actual));
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

    private String canonicalTitle(String value) {
        String stripped = stripSafeBracketQualifiers(safe(value));
        String normalized = normalize(stripped);
        for (String suffix : SAFE_TITLE_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                return normalized.substring(0, normalized.length() - suffix.length());
            }
        }
        return normalized;
    }

    private Set<String> titleVariants(String value) {
        Set<String> variants = new LinkedHashSet<>();
        String title = safe(value);
        addTitleVariant(variants, title);
        addBracketAliases(variants, title);
        return variants;
    }

    private void addBracketAliases(Set<String> variants, String value) {
        String title = safe(value);
        while (!title.isBlank()) {
            Matcher matcher = TRAILING_BRACKET_QUALIFIER.matcher(title);
            if (!matcher.find()) {
                return;
            }
            String qualifier = matcher.group(1);
            if (unsafeTitleQualifier(qualifier)) {
                return;
            }
            String base = title.substring(0, matcher.start()).strip();
            addTitleVariant(variants, base);
            addTitleVariant(variants, qualifier);
            title = base;
        }
    }

    private void addTitleVariant(Set<String> variants, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) {
            variants.add(normalized);
        }
        String canonical = canonicalTitle(value);
        if (!canonical.isBlank()) {
            variants.add(canonical);
        }
    }

    private String stripSafeBracketQualifiers(String value) {
        String stripped = value;
        boolean changed;
        do {
            Matcher matcher = TRAILING_BRACKET_QUALIFIER.matcher(stripped);
            changed = false;
            if (matcher.find() && safeTitleQualifier(matcher.group(1))) {
                stripped = stripped.substring(0, matcher.start()).strip();
                changed = true;
            }
        } while (changed);
        return stripped;
    }

    private boolean safeTitleQualifier(String qualifier) {
        String normalized = normalize(qualifier);
        if (normalized.isBlank()) {
            return false;
        }
        return !unsafeTitleQualifier(qualifier) && SAFE_TITLE_SUFFIXES.stream().anyMatch(normalized::contains);
    }

    private boolean unsafeTitleQualifier(String qualifier) {
        String normalized = normalize(qualifier);
        if (normalized.isBlank()) {
            return false;
        }
        return UNSAFE_TITLE_QUALIFIERS.stream().anyMatch(normalized::contains);
    }

    private boolean artistEquivalent(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        if (expected.length() < 2 || actual.length() < 2) {
            return false;
        }
        return actual.contains(expected) || expected.contains(actual);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
