package com.musio.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "login", description = "Start QQ Music login.")
public class LoginCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        System.out.println("QQ Music login endpoint: GET http://127.0.0.1:18765/api/auth/qqmusic/qr");
        return 0;
    }
}
