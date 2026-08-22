package com.poseidon.codegraph.spi;

/**
 * Task-scoped parser handle.
 *
 * <p>A session may keep temporary in-memory state or an external process alive
 * while one build task processes its files sequentially. It must not be shared
 * between projects and is discarded when the task finishes.
 */
public interface CodeGraphParserSession extends CodeGraphParser, AutoCloseable {

    @Override
    void close();
}
