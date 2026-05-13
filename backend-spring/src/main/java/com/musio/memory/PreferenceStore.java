package com.musio.memory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class PreferenceStore {
    private final SQLiteMemoryDatabase database;

    @Autowired
    public PreferenceStore(SQLiteMemoryDatabase database) {
        this.database = database;
        initialize();
    }

    public synchronized void addCandidate(PreferenceCandidate candidate) {
        if (candidate == null || candidate.name().isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO preference_candidates
                (id, user_id, polarity, name, label, confidence_delta, evidence, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candidate.id());
            statement.setString(2, candidate.userId());
            statement.setString(3, candidate.polarity());
            statement.setString(4, candidate.name());
            statement.setString(5, candidate.label());
            statement.setDouble(6, candidate.confidenceDelta());
            statement.setString(7, candidate.evidence());
            statement.setString(8, candidate.source());
            statement.setString(9, candidate.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add preference candidate.", e);
        }
    }

    public synchronized List<PreferenceCandidate> candidates(String userId, Instant since, int limit) {
        String sql = """
                SELECT id, user_id, polarity, name, label, confidence_delta, evidence, source, created_at
                FROM preference_candidates
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
                List<PreferenceCandidate> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(readCandidate(resultSet));
                }
                return List.copyOf(values);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read preference candidates.", e);
        }
    }

    public synchronized List<PreferenceItem> items(String userId, int limit) {
        String sql = """
                SELECT user_id, preference_key, polarity, label, confidence, evidence, updated_at
                FROM preference_items
                WHERE user_id = ?
                ORDER BY confidence DESC, updated_at DESC
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeUserId(userId));
            statement.setInt(2, Math.max(1, Math.min(200, limit)));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PreferenceItem> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(readItem(resultSet));
                }
                return List.copyOf(values);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read preference items.", e);
        }
    }

    public synchronized List<String> userIds() {
        String sql = """
                SELECT user_id FROM preference_candidates
                UNION
                SELECT user_id FROM preference_items
                ORDER BY user_id
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                String userId = resultSet.getString("user_id");
                if (userId != null && !userId.isBlank()) {
                    values.add(userId.strip());
                }
            }
            return List.copyOf(values);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read preference users.", e);
        }
    }

    public synchronized void upsertItem(PreferenceItem item) {
        if (item == null || item.key().isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO preference_items
                (user_id, preference_key, polarity, label, confidence, evidence, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id, preference_key) DO UPDATE SET
                    polarity = excluded.polarity,
                    label = excluded.label,
                    confidence = excluded.confidence,
                    evidence = excluded.evidence,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.userId());
            statement.setString(2, item.key());
            statement.setString(3, item.polarity());
            statement.setString(4, item.label());
            statement.setDouble(5, item.confidence());
            statement.setString(6, item.evidence());
            statement.setString(7, item.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert preference item.", e);
        }
    }

    private void initialize() {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS preference_candidates (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        polarity TEXT NOT NULL,
                        name TEXT NOT NULL,
                        label TEXT NOT NULL,
                        confidence_delta REAL NOT NULL,
                        evidence TEXT NOT NULL DEFAULT '',
                        source TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_preference_candidates_user_time ON preference_candidates(user_id, created_at)");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS preference_items (
                        user_id TEXT NOT NULL,
                        preference_key TEXT NOT NULL,
                        polarity TEXT NOT NULL,
                        label TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        evidence TEXT NOT NULL DEFAULT '',
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(user_id, preference_key)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_preference_items_user_confidence ON preference_items(user_id, confidence)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize preference store.", e);
        }
    }

    private PreferenceCandidate readCandidate(ResultSet resultSet) throws SQLException {
        return new PreferenceCandidate(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                resultSet.getString("polarity"),
                resultSet.getString("name"),
                resultSet.getString("label"),
                resultSet.getDouble("confidence_delta"),
                resultSet.getString("evidence"),
                resultSet.getString("source"),
                Instant.parse(resultSet.getString("created_at"))
        );
    }

    private PreferenceItem readItem(ResultSet resultSet) throws SQLException {
        return new PreferenceItem(
                resultSet.getString("user_id"),
                resultSet.getString("preference_key"),
                resultSet.getString("polarity"),
                resultSet.getString("label"),
                resultSet.getDouble("confidence"),
                resultSet.getString("evidence"),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private String safeUserId(String userId) {
        return userId == null || userId.isBlank() ? "local" : userId.strip();
    }
}
