package com.poseidon.codegraph.spi;

import java.util.List;

/**
 * Provider SPI for adapters that need to expose parsers dynamically.
 */
public interface CodeGraphParserProvider {

    List<CodeGraphParser> parsers();
}
