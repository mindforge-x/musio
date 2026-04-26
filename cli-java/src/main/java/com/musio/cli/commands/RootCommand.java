package com.musio.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "musio",
        mixinStandardHelpOptions = true,
        version = "musio 0.1.0-SNAPSHOT",
        subcommands = {
                WebCommand.class,
                ChatCommand.class,
                LoginCommand.class,
                StatusCommand.class,
                StopCommand.class,
                DoctorCommand.class
        }
)
public class RootCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return new WebCommand().call();
    }
}
