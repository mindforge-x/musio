package com.musio.cli.repl;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class ChatRepl {
    public void run() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("musio")
                    .build();

            terminal.writer().println("Musio chat REPL. Type exit to quit.");
            terminal.writer().flush();
            while (true) {
                String line = reader.readLine("musio> ");
                if (line == null || "exit".equalsIgnoreCase(line.trim())) {
                    return;
                }
                terminal.writer().println("Backend chat wiring is pending. Input: " + line);
                terminal.writer().flush();
            }
        }
    }
}
