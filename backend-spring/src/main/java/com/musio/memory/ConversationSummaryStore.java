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
import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationSummaryStore {
    private final SQLiteMemoryDatabase database;
    private final ObjectMapper objectMapper;
    private volatile boolean ftsEnabled;

    @Autowired
    public ConversationSummaryStore(SQLiteMemoryDatabase database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper.findAndRegisterModules();
        initialize();
    }

    public synchronized void upsert(ConversationSummary summary) {
        if (summary == null || summary.summary().isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO conversation_summaries
                (id, user_id, summary, keywords_json, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    user_id = excluded.user_id,
                    summary = excluded.summary,
                    keywords_json = excluded.keywords_json,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, summary.id());
            statement.setString(2, summary.userId());
            statement.setString(3, summary.summary());
            statement.setString(4, json(summary.keywords()));
            statement.setString(5, summary.updatedAt().toString());
            statement.executeUpdate();
            upsertFts(connection, summary);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert conversation summary.", e);
        }
    }

    public synchronized List<ConversationSummary> search(String userId, String query, int limit) {
        String normalizedQuery = query == null ? "" : query.strip();
        if (ftsEnabled && !normalizedQuery.isBlank()) {
            try {
                List<ConversationSummary> ftsResults = searchFts(userId, normalizedQuery, limit);
                if (!ftsResults.isEmpty()) {
                    return ftsResults;
                }
            } catch (SQLException ignored) {
                ftsEnabled = false;
            }
        }
        return searchLike(userId, normalizedQuery, limit);
    }

    private void initialize() {
        try (Connection connection = database.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS conversation_summaries (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        keywords_json TEXT NOT NULL DEFAULT '[]',
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conversation_summaries_user_time ON conversation_summaries(user_id, updated_at)");
            try {
                statement.executeUpdate("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS conversation_summary_fts
                        USING fts5(id UNINDEXED, user_id UNINDEXED, summary, keywords)
                        """);
                ftsEnabled = true;
            } catch (SQLException ignored) {
                ftsEnabled = false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize conversation summary store.", e);
        }
    }

    private void upsertFts(Connection connection, ConversationSummary summary) {
        if (!ftsEnabled) {
            return;
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM conversation_summary_fts WHERE id = ?");
             PreparedStatement insert = connection.prepareStatement("INSERT INTO conversation_summary_fts(id, user_id, summary, keywords) VALUES (?, ?, ?, ?)")) {
            delete.setString(1, summary.id());
            delete.executeUpdate();
            insert.setString(1, summary.id());
            insert.setString(2, summary.userId());
            insert.setString(3, summary.summary());
            insert.setString(4, String.join(" ", summary.keywords()));
            insert.executeUpdate();
        } catch (SQLException ignored) {
            ftsEnabled = false;
        }
    }

    private List<ConversationSummary> searchFts(String userId, String query, int limit) throws SQLException {
        String sql = """
                SELECT s.id, s.user_id, s.summary, s.keywords_json, s.updated_at
                FROM conversation_summary_fts
                JOIN conversation_summaries s ON s.id = conversation_summary_fts.id
                WHERE conversation_summary_fts.user_id = ? AND conversation_summary_fts MATCH ?
                ORDER BY rank
                LIMIT ?
                """;
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeUserId(userId));
            statement.setString(2, ftsQuery(query));
            statement.setInt(3, Math.max(1, Math.min(20, limit)));
            try (ResultSet resultSet = statement.executeQuery()) {
                return readSummaries(resultSet);
            }
        }
    }

    private List<ConversationSummary> searchLike(String userId, String query, int limit) {
        String sql = """
                SELECT id, user_id, summary, keywords_json, updated_at
                FROM conversation_summaries
                WHERE user_id = ? %s
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(query.isBlank() ? "" : "AND summary LIKE ?");
        try (Connection connection = database.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeUserId(userId));
            int index = 2;
            if (!query.isBlank()) {
                statement.setString(index++, "%" + query + "%");
            }
            statement.setInt(index, Math.max(1, Math.min(20, limit)));
            try (ResultSet resultSet = statement.executeQuery()) {
                return readSummaries(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search conversation summaries.", e);
        }
    }

    private List<ConversationSummary> readSummaries(ResultSet resultSet) throws SQLException {
        List<ConversationSummary> summaries = new ArrayList<>();
        while (resultSet.next()) {
            summaries.add(new ConversationSummary(
                    resultSet.getString("id"),
                    resultSet.getString("user_id"),
                    resultSet.getString("summary"),
                    readKeywords(resultSet.getString("keywords_json")),
                    Instant.parse(resultSet.getString("updated_at"))
            ));
        }
        return List.copyOf(summaries);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readKeywords(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String ftsQuery(String query) {
        String normalized = query == null ? "" : query.strip().replace("\"", " ");
        return normalized.isBlank() ? "\"\"" : "\"" + normalized + "\"";
    }

    private String safeUserId(String userId) {
        return userId == null || userId.isBlank() ? "local" : userId.strip();
    }
}
