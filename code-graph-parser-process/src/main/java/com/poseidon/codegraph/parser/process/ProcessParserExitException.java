package com.poseidon.codegraph.parser.process;

import java.util.List;

public class ProcessParserExitException extends ProcessParserException {

    private final int exitCode;
    private final String stderr;

    public ProcessParserExitException(String language, List<String> command, int exitCode, String stderr) {
        super("External parser failed: language=" + language
            + ", exitCode=" + exitCode
            + ", command=" + command
            + ", stderr=" + abbreviate(stderr), language, command);
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    public int exitCode() {
        return exitCode;
    }

    public String stderr() {
        return stderr;
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 800) {
            return value;
        }
        return value.substring(0, 800) + "...";
    }
}
