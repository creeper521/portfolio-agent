package com.portfolio.agent.evaluation.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EvalCliArguments {

    public enum Command {
        VALIDATE,
        OFFLINE,
        PROVIDER,
        PERIODIC,
        LEGACY
    }

    private static final Map<String, Command> COMMANDS = Map.of(
            "validate", Command.VALIDATE,
            "offline", Command.OFFLINE,
            "provider", Command.PROVIDER,
            "periodic", Command.PERIODIC,
            "legacy", Command.LEGACY);

    private final Command command;
    private final Map<String, String> options;
    private final List<String> flags;

    private EvalCliArguments(Command command,
                             Map<String, String> options,
                             List<String> flags) {
        this.command = command;
        this.options = options;
        this.flags = flags;
    }

    public static EvalCliArguments parse(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("missing command");
        }
        Command command = COMMANDS.get(args[0]);
        if (command == null) {
            throw new IllegalArgumentException("unknown command: " + args[0]);
        }
        Map<String, String> options = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        int index = 1;
        while (index < args.length) {
            String token = args[index];
            if (token.startsWith("--")) {
                String name = token.substring(2);
                if (options.containsKey(name) || flags.contains(name)) {
                    throw new IllegalArgumentException("duplicate argument: " + name);
                }
                if (name.startsWith("authorize")) {
                    flags.add(name);
                } else {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value for " + name);
                    }
                    options.put(name, args[index + 1]);
                    index++;
                }
            } else {
                throw new IllegalArgumentException("unknown argument: " + token);
            }
            index++;
        }
        return new EvalCliArguments(command, options, flags);
    }

    public Command getCommand() { return command; }

    public String required(String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required argument --" + name);
        }
        return value;
    }

    public String optional(String name) {
        return options.get(name);
    }

    public Path requiredPath(String name) {
        return Path.of(required(name));
    }

    public boolean hasFlag(String name) {
        return flags.contains(name);
    }

    public List<String> getFlagNames() {
        return List.copyOf(flags);
    }
}
