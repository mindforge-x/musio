package com.musio.cli.commands;

import com.musio.cli.setup.StartupWorkflow;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "web", description = "Start local Musio services and open the web console.")
public class WebCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return new StartupWorkflow().run();
    }
}
