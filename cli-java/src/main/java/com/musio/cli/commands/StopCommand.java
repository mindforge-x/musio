package com.musio.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "stop", description = "Stop local Musio services.")
public class StopCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("Stop command is reserved for the local process manager.");
        return 0;
    }
}
