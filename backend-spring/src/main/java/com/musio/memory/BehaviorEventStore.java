package com.musio.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BehaviorEventStore {
    private final SQLiteMemoryDatabase database;
    private final ObjectMapper objectMapper;

    @Autowired
    public BehaviorEventStore(SQLiteMemoryDatabase database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper.findAndRegisterModules();
        initialize();
    }

    public BehaviorEventStore(SQLiteMemoryDatabase database) {
        this(database, new ObjectMapper().findAndRegisterModules());
    }

    public synchronized void append(BehaviorEvent event) {
        if (event == null || event.type().isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO behavior_events
                (id, user_id, type, source, song_id, song_title, artists_json, evidence, payload_json, confidence, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.id());
            statement.setString(2, event.userId());
            statement.setString(3, event.type());
            statement.setString(4, event.source());
            statement.setString(5, event.songId());
            statement.setString(6, event.songTitle());
            statement.setString(7, json(event.artists()));
            statement.setString(8, event.evidence());
            statement.setString(9, json(event.payload()));
            statement.setDouble(10, event.confidence());
            statement.setString(11, event.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to append behavior event.", e);
        }
    }

    public synchronized List<BehaviorEvent> recent(String userId, Instant since, int limit) {
        String sql = """
                SELECT id, user_id, type, source, song_id, song_title, artists_json, evidence, payload_json, confidence, created_at
                FROM behavior_events
                WHERE user_id = ? AND created_at >= ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeUserId(userId));
            statement.setString(2, (since == null ? Instant.EPOCH : since).toString());
            statement.setInt(3, Math.max(1, Math.min(500, limit)));
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.ArrayList<BehaviorEvent> events = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    events.add(readEvent(resultSet));
                }
                return List.copyOf(events);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read behavior events.", e);
        }
    }

    private void initialize() {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS behavior_events (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        source TEXT NOT NULL,
                        song_id TEXT NOT NULL DEFAULT '',
                        song_title TEXT NOT NULL DEFAULT '',
                        artists_json TEXT NOT NULL DEFAULT '[]',
                        evidence TEXT NOT NULL DEFAULT '',
                        payload_json TEXT NOT NULL DEFAULT '{}',
                        confidence REAL NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_behavior_events_user_time ON behavior_events(user_id, created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_behavior_events_type ON behavior_events(type)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize behavior event store.", e);
        }
    }

    private BehaviorEvent readEvent(ResultSet resultSet) throws SQLException {
        return new BehaviorEvent(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                resultSet.getString("type"),
                resultSet.getString("source"),
                resultSet.getString("song_id"),
                resultSet.getString("song_title"),
                readStringList(resultSet.getString("artists_json")),
                resultSet.getString("evidence"),
                readMap(resultSet.getString("payload_json")),
                resultSet.getDouble("confidence"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String safeUserId(String userId) {
        return userId == null || userId.isBlank() ? "local" : userId.strip();
    }
}
