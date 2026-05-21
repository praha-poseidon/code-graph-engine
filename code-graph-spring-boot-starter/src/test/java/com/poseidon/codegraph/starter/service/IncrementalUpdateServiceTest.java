package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IncrementalUpdateServiceTest {

    @Mock
    private CodePackageRepository packageRepository;
    @Mock
    private CodeUnitRepository unitRepository;
    @Mock
    private CodeFunctionRepository functionRepository;
    @Mock
    private CodeRelationshipRepository relationshipRepository;
    @Mock
    private CodeEndpointRepository endpointRepository;

    private IncrementalUpdateService service;

    @BeforeEach
    void setUp() {
        service = new IncrementalUpdateService(
            packageRepository,
            unitRepository,
            functionRepository,
            relationshipRepository,
            endpointRepository);

        lenient().when(packageRepository.findExistingPackagesByQualifiedNames(any(), any())).thenReturn(Set.of());
        lenient().when(unitRepository.findExistingUnitsByQualifiedNames(any(), any())).thenReturn(Set.of());
        lenient().when(functionRepository.findExistingFunctionsByQualifiedNames(any(), any())).thenReturn(Set.of());
        lenient().when(functionRepository.findFunctionsByQualifiedNames(any(), any())).thenReturn(List.of());
        lenient().when(relationshipRepository.findExistingStructureRelationships(any(), any())).thenReturn(Set.of());
        lenient().when(endpointRepository.findExistingEndpointsByIds(any(), any())).thenReturn(Set.of());
        lenient().when(endpointRepository.findEndpointsByMatchIdentity(any(), any())).thenReturn(List.of());
    }

    @Test
    void handleFileAddedParsesJavaFileAndWritesGraph(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = tempDir.resolve("src/main/java");
        Path classesRoot = tempDir.resolve("target/classes");
        Path sourceFile = sourceRoot.resolve("demo/App.java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(sourceFile, """
            package demo;

            public class App {
                public void run() {
                }
            }
            """);

        service.handleFileAdded(
            "demo-project",
            sourceFile.toString(),
            "src/main/java/demo/App.java",
            "git@example/demo.git",
            "main",
            new String[] {classesRoot.toString()},
            new String[] {sourceRoot.toString()},
            List.of(),
            List.of());

        ArgumentCaptor<List> packageCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> unitCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> functionCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> relationshipCaptor = ArgumentCaptor.forClass(List.class);

        verify(packageRepository).insertPackagesBatch(packageCaptor.capture());
        verify(unitRepository).insertUnitsBatch(unitCaptor.capture());
        verify(functionRepository).insertFunctionsBatch(functionCaptor.capture());
        verify(relationshipRepository, atLeastOnce()).insertRelationshipsBatch(relationshipCaptor.capture());

        assertThat(packageCaptor.getValue()).hasSize(1);
        assertThat(unitCaptor.getValue()).hasSize(1);
        assertThat(functionCaptor.getValue()).hasSize(1);
        assertThat(relationshipCaptor.getAllValues().stream().flatMap(List::stream))
            .anySatisfy(relationship -> assertThat(relationship).hasFieldOrPropertyWithValue("projectName", "demo-project"));
    }

    @Test
    void handleFileDeletedDeletesExistingUnitsFunctionsAndEndpoints() {
        lenient().when(relationshipRepository.findWhoCallsMe("demo-project", "src/main/java/demo/App.java"))
            .thenReturn(List.of("src/main/java/demo/App.java"));
        lenient().when(unitRepository.findUnitsByProjectFilePath("demo-project", "src/main/java/demo/App.java"))
            .thenReturn(List.of(unit("unit:demo.App")));
        lenient().when(functionRepository.findFunctionsByProjectFilePath("demo-project", "src/main/java/demo/App.java"))
            .thenReturn(List.of(function("fn:demo.App.run()")));
        lenient().when(endpointRepository.findEndpointsByProjectFilePath("demo-project", "src/main/java/demo/App.java"))
            .thenReturn(List.of(endpoint("endpoint:demo.App.run")));

        service.handleFileDeleted(
            "demo-project",
            "/workspace/src/main/java/demo/App.java",
            "src/main/java/demo/App.java",
            "git@example/demo.git",
            "main",
            new String[] {"/workspace/target/classes"},
            new String[] {"/workspace/src/main/java"});

        verify(unitRepository).deleteById("demo-project", "unit:demo.App");
        verify(functionRepository).deleteById("demo-project", "unit:demo.App");
        verify(endpointRepository).deleteById("demo-project", "unit:demo.App");
        verify(unitRepository).deleteById("demo-project", "fn:demo.App.run()");
        verify(functionRepository).deleteById("demo-project", "fn:demo.App.run()");
        verify(endpointRepository).deleteById("demo-project", "fn:demo.App.run()");
        verify(unitRepository).deleteById("demo-project", "endpoint:demo.App.run");
        verify(functionRepository).deleteById("demo-project", "endpoint:demo.App.run");
        verify(endpointRepository).deleteById("demo-project", "endpoint:demo.App.run");
    }

    @Test
    void handleFileModifiedWrapsProcessingFailures() {
        lenient().when(relationshipRepository.findWhoCallsMe("demo-project", "src/main/java/demo/Missing.java"))
            .thenReturn(List.of());
        lenient().when(unitRepository.findUnitsByProjectFilePath("demo-project", "src/main/java/demo/Missing.java"))
            .thenReturn(List.of());
        lenient().when(functionRepository.findFunctionsByProjectFilePath("demo-project", "src/main/java/demo/Missing.java"))
            .thenReturn(List.of());
        lenient().when(endpointRepository.findEndpointsByProjectFilePath("demo-project", "src/main/java/demo/Missing.java"))
            .thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.handleFileModified(
                "demo-project",
                "/workspace/src/main/java/demo/Missing.java",
                "src/main/java/demo/Missing.java",
                "git@example/demo.git",
                "main",
                new String[] {"/workspace/target/classes"},
                new String[] {"/workspace/src/main/java"}))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("处理文件修改失败: src/main/java/demo/Missing.java");
    }

    private CodeUnitDO unit(String id) {
        CodeUnitDO unit = new CodeUnitDO();
        unit.setId(id);
        unit.setName(id);
        unit.setQualifiedName(id);
        unit.setLanguage("java");
        unit.setProjectName("demo-project");
        unit.setProjectFilePath("src/main/java/demo/App.java");
        unit.setUnitType("class");
        return unit;
    }

    private CodeFunctionDO function(String id) {
        CodeFunctionDO function = new CodeFunctionDO();
        function.setId(id);
        function.setName(id);
        function.setQualifiedName(id);
        function.setLanguage("java");
        function.setProjectName("demo-project");
        function.setProjectFilePath("src/main/java/demo/App.java");
        function.setIsPlaceholder(false);
        return function;
    }

    private CodeEndpointDO endpoint(String id) {
        CodeEndpointDO endpoint = new CodeEndpointDO();
        endpoint.setId(id);
        endpoint.setName(id);
        endpoint.setQualifiedName(id);
        endpoint.setLanguage("java");
        endpoint.setProjectName("demo-project");
        endpoint.setProjectFilePath("src/main/java/demo/App.java");
        endpoint.setEndpointType("HTTP");
        endpoint.setDirection("inbound");
        endpoint.setHttpMethod("GET");
        endpoint.setPath("/app");
        endpoint.setNormalizedPath("/app");
        return endpoint;
    }
}
