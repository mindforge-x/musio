package com.musio.cli.commands;

import com.musio.cli.process.ToolProbe;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Check local runtime dependencies.")
public class DoctorCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        ToolProbe probe = new ToolProbe();
        for (String tool : List.of("java", "mvn", "python3", "node", "npm")) {
            System.out.printf("%-8s %s%n", tool, probe.exists(tool) ? "ok" : "missing");
        }
        return 0;
    }
}
