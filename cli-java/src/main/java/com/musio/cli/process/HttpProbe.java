package com.musio.cli.process;

import java.io.IOException;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class HttpProbe {
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Optional<String> get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return Optional.of(response.body());
            }
            return Optional.of("HTTP " + response.statusCode() + " from " + uri);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    public boolean isReady(URI uri) {
        return get(uri)
                .filter(body -> !body.startsWith("HTTP "))
                .isPresent();
    }

    public boolean canConnect(URI uri) {
        int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(uri);
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(uri.getHost(), port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean waitUntilReady(URI uri, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (isReady(uri)) {
                return true;
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private int defaultPort(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return 80;
    }
}
