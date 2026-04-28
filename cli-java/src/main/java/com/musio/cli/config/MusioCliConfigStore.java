package com.musio.cli.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class MusioCliConfigStore {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 18765;
    private static final int DEFAULT_WEB_PORT = 18766;
    private static final int DEFAULT_QQMUSIC_SIDECAR_PORT = 18767;
    private static final Map<String, String> ALIASES = Map.of(
            "qqmusic.sidecar.host", "providers.qqmusic.sidecar_host",
            "qqmusic.sidecar.port", "providers.qqmusic.sidecar_port",
            "qqmusic.sidecar.base_url", "providers.qqmusic.sidecar_base_url"
    );

    private final Path configPath;

    public MusioCliConfigStore() {
        this(defaultConfigPath());
    }

    public MusioCliConfigStore(Path configPath) {
        this.configPath = configPath;
    }

    public MusioCliConfig load() {
        Map<String, String> values = readValues();
        String serverHost = value(values, "server.host", DEFAULT_HOST);
        int serverPort = portValue(values, "server.port", DEFAULT_SERVER_PORT);
        String webHost = value(values, "web.host", DEFAULT_HOST);
        int webPort = portValue(values, "web.port", DEFAULT_WEB_PORT);
        String sidecarHost = value(values, "providers.qqmusic.sidecar_host", DEFAULT_HOST);
        int sidecarPort = portValue(values, "providers.qqmusic.sidecar_port", DEFAULT_QQMUSIC_SIDECAR_PORT);

        if (!values.containsKey("providers.qqmusic.sidecar_host")
                || !values.containsKey("providers.qqmusic.sidecar_port")) {
            URI sidecarBaseUri = parseUri(values.get("providers.qqmusic.sidecar_base_url")).orElse(null);
            if (sidecarBaseUri != null) {
                if (!values.containsKey("providers.qqmusic.sidecar_host") && sidecarBaseUri.getHost() != null) {
                    sidecarHost = sidecarBaseUri.getHost();
                }
                if (!values.containsKey("providers.qqmusic.sidecar_port") && sidecarBaseUri.getPort() > 0) {
                    sidecarPort = sidecarBaseUri.getPort();
                }
            }
        }

        return new MusioCliConfig(configPath, serverHost, serverPort, webHost, webPort, sidecarHost, sidecarPort);
    }

    public Map<String, String> displayValues() {
        MusioCliConfig config = load();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("config.path", config.configPath().toString());
        values.put("server.host", config.serverHost());
        values.put("server.port", Integer.toString(config.serverPort()));
        values.put("web.host", config.webHost());
        values.put("web.port", Integer.toString(config.webPort()));
        values.put("providers.qqmusic.sidecar_host", config.qqMusicSidecarHost());
        values.put("providers.qqmusic.sidecar_port", Integer.toString(config.qqMusicSidecarPort()));
        values.put("providers.qqmusic.sidecar_base_url", config.qqMusicSidecarBaseUrl());
        return values;
    }

    public Optional<String> displayValue(String key) {
        return Optional.ofNullable(displayValues().get(canonicalKey(key)));
    }

    public String set(String key, String value) {
        String canonicalKey = canonicalKey(key);
        validate(canonicalKey, value);
        writeValue(canonicalKey, normalizedValue(canonicalKey, value));
        return canonicalKey;
    }

    public Path configPath() {
        return configPath;
    }

    public static String canonicalKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }

    private void writeValue(String fullKey, String value) {
        String section = section(fullKey);
        String key = localKey(fullKey);
        try {
            if (configPath.getParent() != null) {
                Files.createDirectories(configPath.getParent());
            }
            if (!Files.isRegularFile(configPath)) {
                Files.writeString(
                        configPath,
                        initialContent(section, key, value),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
                return;
            }

            List<String> lines = Files.readAllLines(configPath);
            List<String> updated = new ArrayList<>(lines);
            int sectionStart = findSection(lines, section);
            if (sectionStart < 0) {
                appendSection(updated, section, key, value);
            } else if (!replaceInSection(updated, sectionStart, key, value)) {
                insertInSection(updated, sectionStart, key, value);
            }
            Files.write(configPath, updated, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to update Musio config file: " + configPath, e);
        }
    }

    private boolean replaceInSection(List<String> lines, int sectionStart, String key, String value) {
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            String trimmed = stripComment(lines.get(i)).trim();
            if (isSection(trimmed)) {
                return false;
            }
            int equals = trimmed.indexOf('=');
            if (equals < 0) {
                continue;
            }
            if (trimmed.substring(0, equals).trim().equals(key)) {
                lines.set(i, key + " = " + tomlValue(value));
                return true;
            }
        }
        return false;
    }

    private void insertInSection(List<String> lines, int sectionStart, String key, String value) {
        int insertAt = lines.size();
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            if (isSection(stripComment(lines.get(i)).trim())) {
                insertAt = i;
                break;
            }
        }
        lines.add(insertAt, key + " = " + tomlValue(value));
    }

    private void appendSection(List<String> lines, String section, String key, String value) {
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
            lines.add("");
        }
        lines.add("[" + section + "]");
        lines.add(key + " = " + tomlValue(value));
    }

    private String initialContent(String section, String key, String value) {
        return "# Musio user configuration.\n\n"
                + "[" + section + "]\n"
                + key + " = " + tomlValue(value) + "\n";
    }

    private Map<String, String> readValues() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(configPath)) {
            return values;
        }
        String section = "";
        try {
            for (String rawLine : Files.readAllLines(configPath)) {
                String line = stripComment(rawLine).trim();
                if (line.isBlank()) {
                    continue;
                }
                if (isSection(line)) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String key = line.substring(0, equals).trim();
                String value = unquote(line.substring(equals + 1).trim());
                values.put(section.isBlank() ? key : section + "." + key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Musio config file: " + configPath, e);
        }
        return values;
    }

    private static Path defaultConfigPath() {
        String configured = System.getenv("MUSIO_CONFIG");
        if (configured != null && !configured.isBlank()) {
            return expandHome(configured);
        }
        return Path.of(System.getProperty("user.home"), ".musio", "config.toml")
                .toAbsolutePath()
                .normalize();
    }

    private static Path expandHome(String value) {
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
        if (value.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), value.substring(2)).toAbsolutePath().normalize();
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static String value(Map<String, String> values, String key, String defaultValue) {
        String value = values.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int portValue(Map<String, String> values, String key, int defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                return defaultValue;
            }
            return port;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void validate(String key, String value) {
        if (!displayKeys().contains(key)) {
            throw new IllegalArgumentException("Unsupported config key: " + key);
        }
        if (isPortKey(key)) {
            int port;
            try {
                port = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be a number: " + value);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535: " + value);
            }
        }
    }

    private static List<String> displayKeys() {
        return List.of(
                "server.host",
                "server.port",
                "web.host",
                "web.port",
                "providers.qqmusic.sidecar_host",
                "providers.qqmusic.sidecar_port",
                "providers.qqmusic.sidecar_base_url"
        );
    }

    private static String normalizedValue(String key, String value) {
        if (isPortKey(key)) {
            return Integer.toString(Integer.parseInt(value));
        }
        return value.trim();
    }

    private static boolean isPortKey(String key) {
        return key.endsWith(".port") || key.endsWith("_port");
    }

    private static String tomlValue(String value) {
        if (value.matches("[0-9]+")) {
            return value;
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String stripComment(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                inQuotes = !inQuotes;
            }
            if (current == '#' && !inQuotes) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isSection(String line) {
        return line.startsWith("[") && line.endsWith("]");
    }

    private static int findSection(List<String> lines, String section) {
        for (int i = 0; i < lines.size(); i++) {
            String line = stripComment(lines.get(i)).trim();
            if (line.equals("[" + section + "]")) {
                return i;
            }
        }
        return -1;
    }

    private static String section(String fullKey) {
        return fullKey.substring(0, fullKey.lastIndexOf('.'));
    }

    private static String localKey(String fullKey) {
        return fullKey.substring(fullKey.lastIndexOf('.') + 1);
    }

    private static Optional<URI> parseUri(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new URI(value));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
