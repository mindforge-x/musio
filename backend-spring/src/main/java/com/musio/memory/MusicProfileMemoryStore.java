package com.musio.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.config.MusioConfigService;
import com.musio.model.MusicProfileMemory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class MusicProfileMemoryStore {
    private final ObjectMapper objectMapper;
    private final Path profilePath;

    public MusicProfileMemoryStore(MusioConfigService configService) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.profilePath = configService.config().storage().home().resolve("memory").resolve("music-profile.json");
    }

    public boolean exists() {
        return Files.isRegularFile(profilePath);
    }

    public Optional<MusicProfileMemory> read() {
        if (!exists()) {
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
            Files.createDirectories(profilePath.getParent());
            objectMapper.writeValue(profilePath.toFile(), profile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store music profile memory.", e);
        }
    }
}
