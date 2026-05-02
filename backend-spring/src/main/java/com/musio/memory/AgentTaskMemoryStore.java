package com.musio.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.musio.config.MusioConfigService;
import com.musio.model.AgentTaskMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class AgentTaskMemoryStore {
    private static final String DEFAULT_USER_ID = "local";

    private final ObjectMapper objectMapper;
    private final Path memoryDir;

    @Autowired
    public AgentTaskMemoryStore(MusioConfigService configService) {
        this(
                configService.config().storage().home().resolve("memory").resolve("agent-task"),
                new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT)
        );
    }

    AgentTaskMemoryStore(Path memoryDir, ObjectMapper objectMapper) {
        this.memoryDir = memoryDir.toAbsolutePath().normalize();
        this.objectMapper = objectMapper.findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<AgentTaskMemory> read(String userId) {
        Path path = pathFor(userId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), AgentTaskMemory.class));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read agent task memory.", e);
        }
    }

    public void write(String userId, AgentTaskMemory memory) {
        Path path = pathFor(userId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), memory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store agent task memory.", e);
        }
    }

    private Path pathFor(String userId) {
        return memoryDir.resolve(safeFileName(userId) + ".json").normalize();
    }

    private String safeFileName(String userId) {
        String value = userId == null || userId.isBlank() ? DEFAULT_USER_ID : userId.trim();
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? DEFAULT_USER_ID : safe;
    }
}
