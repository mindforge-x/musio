package com.musio.cli.commands;

import com.musio.cli.config.MusioCliConfig;
import com.musio.cli.config.MusioCliConfigStore;
import com.musio.cli.process.HttpProbe;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "status", description = "Show local service status.")
public class StatusCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        MusioCliConfig config = new MusioCliConfigStore().load();
        HttpProbe probe = new HttpProbe();
        System.out.println(probe.get(config.systemStatusUri()).orElse("Musio backend is not responding."));
        return 0;
    }
}
