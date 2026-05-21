package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.delta.DeltaScope;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.parser.javajdt.endpoint.EndpointParsingService;
import com.poseidon.codegraph.spi.CodeGraphParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java parser adapter backed by Eclipse JDT.
 */
public class JavaJdtCodeGraphParser implements CodeGraphParser {

    @Override
    public String language() {
        return "java";
    }

    @Override
    public GraphDelta parse(ParseRequest request) {
        EndpointParsingService endpointParsingService = endpointParsingService(request);
        JdtSourceCodeParser parser =
                new JdtSourceCodeParser(
                        toArray(request.dependencies()),
                        toArray(request.sourceRoots()),
                        ProcessorRegistry.createWithEndpoint(endpointParsingService),
                        request.externalValues(),
                        endpointParsingService);

        List<CodePackage> packages = new ArrayList<>();
        List<CodeUnit> units = new ArrayList<>();
        List<CodeFunction> functions = new ArrayList<>();
        List<CodeEndpoint> endpoints = new ArrayList<>();
        List<CodeRelationship> relationships = new ArrayList<>();

        for (String sourceFile : safeList(request.sourceFiles())) {
            CodeGraph graph =
                    parser.parse(
                            sourceFile,
                            request.projectName(),
                            projectFilePath(request, sourceFile),
                            request.gitRepoUrl(),
                            request.gitBranch());
            packages.addAll(graph.getPackagesAsList());
            units.addAll(graph.getUnitsAsList());
            functions.addAll(graph.getFunctionsAsList());
            endpoints.addAll(graph.getEndpointsAsList());
            relationships.addAll(graph.getRelationshipsAsList());
        }

        return new GraphDelta(
                new DeltaScope(
                        request.projectName(),
                        language(),
                        request.gitRepoUrl(),
                        request.gitBranch(),
                        request.projectRoot(),
                        request.sourceFiles(),
                        request.changeType(),
                        Map.of()),
                packages,
                units,
                functions,
                endpoints,
                relationships,
                List.of(),
                List.of(),
                List.of());
    }

    private EndpointParsingService endpointParsingService(ParseRequest request) {
        if ((request.ruleSources() == null || request.ruleSources().isEmpty())
                && (request.traceRuleSources() == null || request.traceRuleSources().isEmpty())) {
            return null;
        }
        return new EndpointParsingService(
                request.ruleSources(),
                request.traceRuleSources(),
                includeBuiltinRules(request));
    }

    private boolean includeBuiltinRules(ParseRequest request) {
        Object value = request.options() != null ? request.options().get("includeBuiltinRules") : null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return true;
    }

    private String projectFilePath(ParseRequest request, String sourceFile) {
        Object explicitProjectFilePath = request.options() != null ? request.options().get("projectFilePath") : null;
        if (explicitProjectFilePath instanceof String value && !value.isBlank()) {
            return value;
        }
        if (request.projectRoot() == null || request.projectRoot().isBlank()) {
            return sourceFile;
        }
        String normalizedRoot = request.projectRoot().replace('\\', '/');
        String normalizedFile = sourceFile.replace('\\', '/');
        if (normalizedFile.startsWith(normalizedRoot + "/")) {
            return normalizedFile.substring(normalizedRoot.length() + 1);
        }
        return sourceFile;
    }

    private String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
