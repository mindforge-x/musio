package com.musio.cli.ui;

public final class CliTimeline {
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String BLUE = "\033[34m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String GRAY = "\033[90m";

    private CliTimeline() {
    }

    public static void step(String title) {
        System.out.println();
        System.out.println(BLUE + "  ◆" + RESET + " " + BOLD + title + RESET);
    }

    public static void branch(String title) {
        System.out.println(GRAY + "  ├─" + RESET + " " + title);
    }

    public static void end(String title) {
        System.out.println(GRAY + "  └─" + RESET + " " + title);
    }

    public static void detail(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + text);
    }

    public static void muted(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + DIM + text + RESET);
    }

    public static void pending(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + BLUE + "○" + RESET + " " + text);
    }

    public static void success(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + GREEN + "●" + RESET + " " + text);
    }

    public static void warning(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + YELLOW + "▲" + RESET + " " + text);
    }

    public static void error(String text) {
        System.out.println(GRAY + "  │" + RESET + "  " + RED + "×" + RESET + " " + text);
    }
}
