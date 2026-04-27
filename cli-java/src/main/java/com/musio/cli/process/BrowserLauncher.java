package com.musio.cli.process;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public class BrowserLauncher {
    public boolean open(URI uri) {
        if (openWithDesktop(uri)) {
            return true;
        }
        return openWithPlatformCommand(uri);
    }

    private boolean openWithDesktop(URI uri) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        try {
            desktop.browse(uri);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean openWithPlatformCommand(URI uri) {
        String url = uri.toString();
        ProcessBuilder builder = command(url);
        if (builder == null) {
            return false;
        }
        try {
            return builder.start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ProcessBuilder command(String url) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        }
        if (osName.contains("mac")) {
            return new ProcessBuilder("open", url);
        }
        if (isWsl()) {
            return new ProcessBuilder("cmd.exe", "/C", "start", "", url);
        }
        return new ProcessBuilder("xdg-open", url);
    }

    private boolean isWsl() {
        String version = System.getProperty("os.version", "").toLowerCase(Locale.ROOT);
        return version.contains("microsoft") || version.contains("wsl");
    }
}
