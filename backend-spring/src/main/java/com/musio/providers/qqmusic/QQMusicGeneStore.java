package com.musio.providers.qqmusic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.config.MusioConfigService;
import com.musio.model.MusicAccountRef;
import com.musio.model.MusicGeneSnapshot;
import com.musio.model.ProviderType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Component
public class QQMusicGeneStore {
    private final ObjectMapper objectMapper;
    private final Path musicGeneRoot;

    public QQMusicGeneStore(MusioConfigService configService) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.musicGeneRoot = configService.config().storage().home().resolve("music-gene");
    }

    public boolean exists(MusicAccountRef account) {
        return Files.isRegularFile(path(account));
    }

    public Optional<MusicGeneSnapshot> read(MusicAccountRef account) {
        Path musicGenePath = path(account);
        if (!Files.isRegularFile(musicGenePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(musicGenePath.toFile(), MusicGeneSnapshot.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read QQ Music gene snapshot.", e);
        }
    }

    public void write(MusicGeneSnapshot snapshot) {
        try {
            Path musicGenePath = path(snapshot.provider(), snapshot.accountKey());
            Files.createDirectories(musicGenePath.getParent());
            objectMapper.writeValue(musicGenePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store QQ Music gene snapshot.", e);
        }
    }

    private Path path(MusicAccountRef account) {
        return path(account.provider(), account.accountKey());
    }

    private Path path(ProviderType provider, String accountKey) {
        if (provider != ProviderType.QQMUSIC) {
            throw new IllegalArgumentException("QQ Music gene store cannot handle provider: " + provider);
        }
        String safeAccountKey = MusicAccountRef.requireSafeAccountKey(accountKey);
        return musicGeneRoot
                .resolve(provider.name().toLowerCase(Locale.ROOT))
                .resolve(safeAccountKey + ".json");
    }
}
