package com.musio.providers.qqmusic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.model.QQMusicCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class QQMusicCredentialStore {
    private final ObjectMapper objectMapper;
    private final Path credentialPath;

    public QQMusicCredentialStore(@Value("${musio.storage.home}") String musioHome) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.credentialPath = Path.of(musioHome, "credentials", "qqmusic.json");
    }

    public boolean exists() {
        return Files.isRegularFile(credentialPath);
    }

    public Optional<QQMusicCredential> read() {
        if (!exists()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(credentialPath.toFile(), QQMusicCredential.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read QQ Music credential.", e);
        }
    }

    public void write(QQMusicCredential credential) {
        try {
            Files.createDirectories(credentialPath.getParent());
            objectMapper.writeValue(credentialPath.toFile(), credential);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store QQ Music credential.", e);
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(credentialPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete QQ Music credential.", e);
        }
    }
}
