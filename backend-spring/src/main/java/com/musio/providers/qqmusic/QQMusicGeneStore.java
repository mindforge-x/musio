package com.musio.providers.qqmusic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.config.MusioConfigService;
import com.musio.model.MusicGeneSnapshot;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class QQMusicGeneStore {
    private final ObjectMapper objectMapper;
    private final Path musicGenePath;

    public QQMusicGeneStore(MusioConfigService configService) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.musicGenePath = configService.config().storage().home().resolve("music-gene").resolve("qqmusic.json");
    }

    public boolean exists() {
        return Files.isRegularFile(musicGenePath);
    }

    public Optional<MusicGeneSnapshot> read() {
        if (!exists()) {
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
            Files.createDirectories(musicGenePath.getParent());
            objectMapper.writeValue(musicGenePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store QQ Music gene snapshot.", e);
        }
    }
}
