package com.musio.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.config.MusioConfigService;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicProfileMemory;
import com.musio.model.ProviderType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Component
public class MusicProfileMemoryStore {
    private final ObjectMapper objectMapper;
    private final Path profileRoot;

    public MusicProfileMemoryStore(MusioConfigService configService) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.profileRoot = configService.config().storage().home().resolve("memory").resolve("music-profile");
    }

    public boolean exists(MusicAccountRef account) {
        return Files.isRegularFile(path(account));
    }

    public Optional<MusicProfileMemory> read(MusicAccountRef account) {
        Path profilePath = path(account);
        if (!Files.isRegularFile(profilePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(profilePath.toFile(), MusicProfileMemory.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read music profile memory.", e);
        }
    }

    public void write(MusicProfileMemory profile) {
        try {
            Path profilePath = path(profile.provider(), profile.accountKey());
            Files.createDirectories(profilePath.getParent());
            objectMapper.writeValue(profilePath.toFile(), profile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store music profile memory.", e);
        }
    }

    private Path path(MusicAccountRef account) {
        return path(account.provider(), account.accountKey());
    }

    private Path path(ProviderType provider, String accountKey) {
        String safeAccountKey = MusicAccountRef.requireSafeAccountKey(accountKey);
        return profileRoot
                .resolve(provider.name().toLowerCase(Locale.ROOT))
                .resolve(safeAccountKey + ".json");
    }
}
