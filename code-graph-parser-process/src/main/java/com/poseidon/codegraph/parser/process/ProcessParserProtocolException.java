package com.poseidon.codegraph.parser.process;

import java.util.List;

public class ProcessParserProtocolException extends ProcessParserException {

    private final String stdout;

    public ProcessParserProtocolException(String message, String language, List<String> command, String stdout) {
        super(message + ": language=" + language
            + ", command=" + command
            + ", stdout=" + abbreviate(stdout), language, command);
        this.stdout = stdout;
    }

    public ProcessParserProtocolException(String message, String language, List<String> command, String stdout, Throwable cause) {
        super(message + ": language=" + language
            + ", command=" + command
            + ", stdout=" + abbreviate(stdout), language, command, cause);
        this.stdout = stdout;
    }

    public String stdout() {
        return stdout;
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 800) {
            return value;
        }
        return value.substring(0, 800) + "...";
    }
}
