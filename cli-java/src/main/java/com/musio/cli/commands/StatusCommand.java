package com.musio.cli.commands;

import com.musio.cli.process.HttpProbe;
import picocli.CommandLine.Command;

import java.net.URI;
import java.util.concurrent.Callable;

@Command(name = "status", description = "Show local service status.")
public class StatusCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        URI statusUri = URI.create("http://127.0.0.1:18765/api/system/status");
        HttpProbe probe = new HttpProbe();
        System.out.println(probe.get(statusUri).orElse("Musio backend is not responding."));
        return 0;
    }
}
