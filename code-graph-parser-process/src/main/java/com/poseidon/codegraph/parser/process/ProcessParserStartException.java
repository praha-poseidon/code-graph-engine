package com.poseidon.codegraph.parser.process;

import java.util.List;

public class ProcessParserStartException extends ProcessParserException {

    public ProcessParserStartException(String language, List<String> command, Throwable cause) {
        super("Cannot start external parser: language=" + language + ", command=" + command, language, command, cause);
    }
}
