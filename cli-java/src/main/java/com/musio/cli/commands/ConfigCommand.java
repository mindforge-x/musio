package com.musio.cli.commands;

import com.musio.cli.config.MusioCliConfigStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "config",
        description = "Read or update Musio local configuration.",
        subcommands = {
                ConfigCommand.Get.class,
                ConfigCommand.Set.class
        }
)
public class ConfigCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        return new Get().call();
    }

    @Command(name = "get", description = "Show Musio configuration.")
    public static class Get implements Callable<Integer> {
        @Parameters(index = "0", arity = "0..1", description = "Optional config key.")
        private String key;

        @Override
        public Integer call() {
            MusioCliConfigStore store = new MusioCliConfigStore();
            if (key != null && !key.isBlank()) {
                String canonicalKey = MusioCliConfigStore.canonicalKey(key);
                String value = store.displayValue(canonicalKey)
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported config key: " + key));
                System.out.println(canonicalKey + " = " + value);
                return 0;
            }

            for (Map.Entry<String, String> entry : store.displayValues().entrySet()) {
                System.out.println(entry.getKey() + " = " + entry.getValue());
            }
            return 0;
        }
    }

    @Command(name = "set", description = "Set a Musio configuration value.")
    public static class Set implements Callable<Integer> {
        @Parameters(index = "0", description = "Config key.")
        private String key;

        @Parameters(index = "1", description = "Config value.")
        private String value;

        @Override
        public Integer call() {
            MusioCliConfigStore store = new MusioCliConfigStore();
            String canonicalKey = store.set(key, value);
            String storedValue = store.displayValue(canonicalKey).orElse(value);
            System.out.println(canonicalKey + " = " + storedValue);
            System.out.println("Updated: " + store.configPath());
            return 0;
        }
    }
}
