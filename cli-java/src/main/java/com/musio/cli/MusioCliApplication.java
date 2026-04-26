package com.musio.cli;

import com.musio.cli.commands.RootCommand;
import picocli.CommandLine;

public final class MusioCliApplication {
    private MusioCliApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new RootCommand()).execute(args);
        System.exit(exitCode);
    }
}
