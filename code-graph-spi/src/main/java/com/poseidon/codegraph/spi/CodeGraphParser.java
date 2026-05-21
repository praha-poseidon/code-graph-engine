package com.poseidon.codegraph.spi;

import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;

/**
 * Parser SPI implemented by language-specific parser adapters.
 */
public interface CodeGraphParser {

    String language();

    GraphDelta parse(ParseRequest request);
}
