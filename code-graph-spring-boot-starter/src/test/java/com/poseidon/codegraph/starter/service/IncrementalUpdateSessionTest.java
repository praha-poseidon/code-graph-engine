package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.spi.CodeGraphParser;
import com.poseidon.codegraph.spi.CodeGraphParserRegistry;
import com.poseidon.codegraph.spi.CodeGraphParserSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class IncrementalUpdateSessionTest {

    @Test
    void everyBuildTaskOwnsAndClosesAnIndependentParserSession() {
        TrackingParser parser = new TrackingParser();
        IncrementalUpdateService service = new IncrementalUpdateService(
            mock(CodePackageRepository.class),
            mock(CodeUnitRepository.class),
            mock(CodeFunctionRepository.class),
            mock(CodeRelationshipRepository.class),
            mock(CodeEndpointRepository.class),
            new CodeGraphParserRegistry(List.of(parser)));

        IncrementalUpdateSession first = service.openSession("go");
        IncrementalUpdateSession second = service.openSession("go");

        assertNotSame(first, second);
        assertEquals(2, parser.opened.get());
        first.close();
        assertEquals(1, parser.closed.get());
        second.close();
        assertEquals(2, parser.closed.get());
    }

    @Test
    void closedTaskCannotProcessAnotherFile() {
        TrackingParser parser = new TrackingParser();
        IncrementalUpdateService service = new IncrementalUpdateService(
            mock(CodePackageRepository.class),
            mock(CodeUnitRepository.class),
            mock(CodeFunctionRepository.class),
            mock(CodeRelationshipRepository.class),
            mock(CodeEndpointRepository.class),
            new CodeGraphParserRegistry(List.of(parser)));
        IncrementalUpdateSession session = service.openSession("go");
        session.close();

        assertThrows(IllegalStateException.class, () -> session.handleFileDeleted(
            "demo", "/repo/main.go", "main.go", null, null, new String[0], new String[0]));
    }

    @Test
    void processingFailureImmediatelyClosesSession() {
        TrackingParser parser = new TrackingParser();
        IncrementalUpdateService service = new IncrementalUpdateService(
            mock(CodePackageRepository.class),
            mock(CodeUnitRepository.class),
            mock(CodeFunctionRepository.class),
            mock(CodeRelationshipRepository.class),
            mock(CodeEndpointRepository.class),
            new CodeGraphParserRegistry(List.of(parser)));
        IncrementalUpdateSession session = service.openSession("go");

        assertThrows(RuntimeException.class, () -> session.handleFileAdded(
            "demo", "/repo/main.go", "main.go", null, null,
            new String[0], new String[0], List.of(), List.of()));

        assertEquals(1, parser.closed.get());
        assertThrows(IllegalStateException.class, () -> session.handleFileDeleted(
            "demo", "/repo/main.go", "main.go", null, null, new String[0], new String[0]));
    }

    @Test
    void closeFailureIsSuppressedWithoutHidingProcessingFailure() {
        TrackingParser parser = new TrackingParser(true);
        IncrementalUpdateService service = new IncrementalUpdateService(
            mock(CodePackageRepository.class),
            mock(CodeUnitRepository.class),
            mock(CodeFunctionRepository.class),
            mock(CodeRelationshipRepository.class),
            mock(CodeEndpointRepository.class),
            new CodeGraphParserRegistry(List.of(parser)));
        IncrementalUpdateSession session = service.openSession("go");

        RuntimeException failure = assertThrows(RuntimeException.class, () -> session.handleFileAdded(
            "demo", "/repo/main.go", "main.go", null, null,
            new String[0], new String[0], List.of(), List.of()));

        assertEquals(1, parser.closed.get());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("close failed", failure.getSuppressed()[0].getMessage());
    }

    private static final class TrackingParser implements CodeGraphParser {
        private final AtomicInteger opened = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final boolean closeFails;

        private TrackingParser() {
            this(false);
        }

        private TrackingParser(boolean closeFails) {
            this.closeFails = closeFails;
        }

        @Override
        public String language() {
            return "go";
        }

        @Override
        public GraphDelta parse(ParseRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CodeGraphParserSession openSession() {
            opened.incrementAndGet();
            return new CodeGraphParserSession() {
                @Override
                public String language() {
                    return "go";
                }

                @Override
                public GraphDelta parse(ParseRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                    if (closeFails) {
                        throw new IllegalStateException("close failed");
                    }
                }
            };
        }
    }
}
