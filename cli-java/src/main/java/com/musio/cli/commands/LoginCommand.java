package com.musio.cli.commands;

import com.musio.cli.config.MusioCliConfig;
import com.musio.cli.config.MusioCliConfigStore;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "login", description = "Start QQ Music login.")
public class LoginCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        MusioCliConfig config = new MusioCliConfigStore().load();
        System.out.println("QQ Music login endpoint: GET " + config.qqMusicLoginUri());
        return 0;
    }
}
