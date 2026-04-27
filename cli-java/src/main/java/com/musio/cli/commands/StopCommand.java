package com.musio.cli.commands;

import com.musio.cli.process.LocalProcessManager;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "stop", description = "Stop local Musio services.")
public class StopCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return new LocalProcessManager().stopServices();
    }
}
