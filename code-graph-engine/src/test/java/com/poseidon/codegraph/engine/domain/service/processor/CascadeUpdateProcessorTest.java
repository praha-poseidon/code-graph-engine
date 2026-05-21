package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.spi.CodeGraphParser;
import com.poseidon.codegraph.spi.CodeGraphParserRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeUpdateProcessorTest {

    @Test
    void supportsOnlyCascadeUpdate() {
        CascadeUpdateProcessor processor = new CascadeUpdateProcessor();
        CodeGraphContext context = new CodeGraphContext();

        context.setChangeType(ChangeType.CASCADE_UPDATE);
        assertThat(processor.support(context)).isTrue();

        context.setChangeType(ChangeType.SOURCE_MODIFIED);
        assertThat(processor.support(context)).isFalse();
    }

    @Test
    void deletesOutgoingCallsAndRebuildsCallRelationshipsWithPlaceholders() {
        CascadeUpdateProcessor processor = new CascadeUpdateProcessor();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        context.setLanguage("java");
        context.setChangeType(ChangeType.CASCADE_UPDATE);
        context.setOldProjectFilePath("src/main/java/demo/Caller.java");
        context.setAbsoluteFilePath("/workspace/src/main/java/demo/Caller.java");
        context.setClasspathEntries(new String[] {"/workspace/target/classes"});
        context.setSourcepathEntries(new String[] {"/workspace/src/main/java"});

        List<String> deletedOutgoing = new ArrayList<>();
        List<CodeFunction> insertedFunctions = new ArrayList<>();
        List<CodeRelationship> insertedRelationships = new ArrayList<>();
        AtomicReference<ParseRequest> parseRequest = new AtomicReference<>();

        context.getWriter().setDeleteFileOutgoingCalls(deletedOutgoing::add);
        context.getWriter().setInsertFunctionsBatch(insertedFunctions::addAll);
        context.getWriter().setInsertRelationshipsBatch(insertedRelationships::addAll);
        context.getReader().setFindExistingFunctionsByQualifiedNames(ids ->
            Set.of("demo::fn:demo.Caller.run()"));
        context.setParserRegistry(new CodeGraphParserRegistry(List.of(new CodeGraphParser() {
            @Override
            public String language() {
                return "java";
            }

            @Override
            public GraphDelta parse(ParseRequest request) {
                parseRequest.set(request);
                CodeRelationship call = new CodeRelationship();
                call.setId(GraphIds.relationshipId(
                    "demo::fn:demo.Caller.run()",
                    RelationshipType.CALLS,
                    "demo::fn:demo.Service.load(java.lang.String)"));
                call.setFromNodeId("demo::fn:demo.Caller.run()");
                call.setToNodeId("demo::fn:demo.Service.load(java.lang.String)");
                call.setRelationshipType(RelationshipType.CALLS);
                call.setLanguage("java");
                call.setProjectName("demo");
                return new GraphDelta(
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(call),
                    List.of(),
                    List.of(),
                    List.of());
            }
        })));

        processor.handle(context);

        assertThat(deletedOutgoing).containsExactly("src/main/java/demo/Caller.java");
        assertThat(parseRequest.get().sourceFiles()).containsExactly("/workspace/src/main/java/demo/Caller.java");
        assertThat(parseRequest.get().sourceRoots()).containsExactly("/workspace/src/main/java");
        assertThat(parseRequest.get().dependencies()).containsExactly("/workspace/target/classes");
        assertThat(parseRequest.get().options()).containsEntry("projectFilePath", "src/main/java/demo/Caller.java");
        assertThat(insertedFunctions)
            .singleElement()
            .satisfies(function -> {
                assertThat(function.getId()).isEqualTo("demo::fn:demo.Service.load(java.lang.String)");
                assertThat(function.getQualifiedName()).isEqualTo("demo.Service.load(java.lang.String)");
                assertThat(function.getName()).isEqualTo("load");
                assertThat(function.getSignature()).isEqualTo("load(java.lang.String)");
                assertThat(function.getIsPlaceholder()).isTrue();
            });
        assertThat(insertedRelationships)
            .singleElement()
            .satisfies(relationship -> {
                assertThat(relationship.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                assertThat(relationship.getFromNodeId()).isEqualTo("demo::fn:demo.Caller.run()");
                assertThat(relationship.getToNodeId()).isEqualTo("demo::fn:demo.Service.load(java.lang.String)");
            });
    }

    @Test
    void fallsBackToProjectFilePathWhenAbsolutePathIsMissing() {
        CascadeUpdateProcessor processor = new CascadeUpdateProcessor();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        context.setLanguage("java");
        context.setChangeType(ChangeType.CASCADE_UPDATE);
        context.setOldProjectFilePath("src/main/java/demo/Caller.java");

        AtomicReference<ParseRequest> parseRequest = new AtomicReference<>();
        context.getWriter().setDeleteFileOutgoingCalls(path -> {});
        context.getWriter().setInsertRelationshipsBatch(relationships -> {});
        context.getReader().setFindExistingFunctionsByQualifiedNames(ids -> Set.copyOf(ids));
        context.setParserRegistry(new CodeGraphParserRegistry(List.of(new CodeGraphParser() {
            @Override
            public String language() {
                return "java";
            }

            @Override
            public GraphDelta parse(ParseRequest request) {
                parseRequest.set(request);
                return new GraphDelta(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            }
        })));

        processor.handle(context);

        assertThat(parseRequest.get().sourceFiles()).containsExactly("src/main/java/demo/Caller.java");
    }
}
