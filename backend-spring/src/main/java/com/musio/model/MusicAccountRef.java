package com.musio.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record MusicAccountRef(
        ProviderType provider,
        String accountKey,
        String userId,
        String euin,
        String displayName,
        boolean authenticated,
        Instant checkedAt
) {
    private static final Pattern SAFE_ACCOUNT_KEY = Pattern.compile("[A-Za-z0-9._-]+");

    public static Optional<MusicAccountRef> qqMusic(
            String userId,
            String euin,
            String displayName,
            boolean authenticated,
            Instant checkedAt
    ) {
        if (!authenticated || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new MusicAccountRef(
                ProviderType.QQMUSIC,
                accountKey(ProviderType.QQMUSIC, userId),
                userId,
                euin,
                displayName,
                true,
                checkedAt
        ));
    }

    public static String accountKey(ProviderType provider, String providerAccountId) {
        if (providerAccountId == null || providerAccountId.isBlank()) {
            throw new IllegalArgumentException("Provider account id is required.");
        }
        String providerPrefix = provider.name().toLowerCase(Locale.ROOT);
        String safeAccountId = providerAccountId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeAccountId.isBlank()) {
            throw new IllegalArgumentException("Provider account id does not contain safe filename characters.");
        }
        return requireSafeAccountKey(providerPrefix + "_" + safeAccountId);
    }

    public static String requireSafeAccountKey(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            throw new IllegalArgumentException("Account key is required.");
        }
        String trimmed = accountKey.trim();
        if (!SAFE_ACCOUNT_KEY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Account key contains unsafe filename characters.");
        }
        return trimmed;
    }
}
