package com.poseidon.codegraph.spi;

import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;

/**
 * Parser SPI implemented by language-specific parser adapters.
 */
public interface CodeGraphParser {

    String language();

    GraphDelta parse(ParseRequest request);

    /**
     * Opens a task-scoped parser handle.
     *
     * <p>The default remains stateless: every parse delegates to the existing
     * parser implementation. Parsers that support a streaming protocol can
     * override this method and keep one process alive for the task.
     */
    default CodeGraphParserSession openSession() {
        CodeGraphParser parser = this;
        return new CodeGraphParserSession() {
            @Override
            public String language() {
                return parser.language();
            }

            @Override
            public GraphDelta parse(ParseRequest request) {
                return parser.parse(request);
            }

            @Override
            public void close() {
                // Stateless parser: nothing to release.
            }
        };
    }
}
