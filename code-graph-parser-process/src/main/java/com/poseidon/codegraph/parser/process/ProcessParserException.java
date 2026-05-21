package com.poseidon.codegraph.parser.process;

import java.util.List;

/**
 * Base exception for external process parser failures.
 */
public class ProcessParserException extends RuntimeException {

    private final String language;
    private final List<String> command;

    public ProcessParserException(String message, String language, List<String> command) {
        super(message);
        this.language = language;
        this.command = command == null ? List.of() : List.copyOf(command);
    }

    public ProcessParserException(String message, String language, List<String> command, Throwable cause) {
        super(message, cause);
        this.language = language;
        this.command = command == null ? List.of() : List.copyOf(command);
    }

    public String language() {
        return language;
    }

    public List<String> command() {
        return command;
    }
}
