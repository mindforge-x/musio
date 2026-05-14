package com.musio.memory;

import com.musio.config.MusioConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class SQLiteMemoryDatabase {
    private final Path databasePath;

    @Autowired
    public SQLiteMemoryDatabase(MusioConfigService configService) {
        this(configService.config().storage().home().resolve("memory").resolve("dynamic-memory.sqlite"));
    }

    public SQLiteMemoryDatabase(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    public Connection openConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(databasePath.getParent());
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA foreign_keys = ON");
            }
            return connection;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open Musio memory SQLite database: " + databasePath, e);
        }
    }

    public Path databasePath() {
        return databasePath;
    }
}
