package com.musio.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "web", description = "Start local Musio services and open the web console.")
public class WebCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("Musio web mode is initialized.");
        System.out.println("Development startup: ./scripts/dev.sh");
        System.out.println("Backend: http://127.0.0.1:18765");
        System.out.println("Web:     http://127.0.0.1:18766");
        return 0;
    }
}
