package com.poseidon.codegraph.parser.process;

import com.poseidon.codegraph.spi.CodeGraphParser;
import com.poseidon.codegraph.spi.CodeGraphParserProvider;

import java.util.List;

/**
 * ServiceLoader provider for configured external process parsers.
 */
public final class ProcessCodeGraphParserProvider implements CodeGraphParserProvider {

    @Override
    public List<CodeGraphParser> parsers() {
        return List.copyOf(ProcessParserConfig.loadParsers());
    }
}
