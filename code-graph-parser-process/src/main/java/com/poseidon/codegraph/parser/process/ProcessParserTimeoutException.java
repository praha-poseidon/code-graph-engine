package com.poseidon.codegraph.parser.process;

import java.time.Duration;
import java.util.List;

public class ProcessParserTimeoutException extends ProcessParserException {

    public ProcessParserTimeoutException(String language, List<String> command, Duration timeout) {
        super("External parser timed out: language=" + language
            + ", timeout=" + timeout.toSeconds() + "s, command=" + command, language, command);
    }
}
