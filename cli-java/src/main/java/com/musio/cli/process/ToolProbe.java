package com.musio.cli.process;

import java.io.IOException;
import java.util.Locale;

public class ToolProbe {
    public boolean exists(String command) {
        String shell = isWindows() ? "cmd" : "sh";
        String flag = isWindows() ? "/c" : "-c";
        String lookup = isWindows() ? "where " + command : "command -v " + command;
        ProcessBuilder builder = new ProcessBuilder(shell, flag, lookup);
        try {
            return builder.start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
