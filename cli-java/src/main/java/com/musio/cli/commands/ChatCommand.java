package com.musio.cli.commands;

import com.musio.cli.repl.ChatRepl;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "chat", description = "Open the terminal chat REPL.")
public class ChatCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        new ChatRepl().run();
        return 0;
    }
}
