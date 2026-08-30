package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.DeltaScope;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.spi.CodeGraphParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Minimal protocol fixture; real Java semantics live in code-graph-parser-java tests. */
final class TestCodeGraphParser implements CodeGraphParser {
    private static final Pattern PACKAGE = Pattern.compile("package\\s+([\\w.]+)");
    private static final Pattern TYPE = Pattern.compile("(?:class|interface|record|enum)\\s+(\\w+)");

    @Override
    public String language() {
        return "java";
    }

    @Override
    public GraphDelta parse(ParseRequest request) {
        try {
            String sourceFile = request.sourceFiles().getFirst();
            String source = Files.readString(Path.of(sourceFile));
            String packageName = match(PACKAGE, source, "");
            String typeName = match(TYPE, source, "App");
            String qualifiedType = packageName.isBlank() ? typeName : packageName + "." + typeName;
            String methodName = source.contains("renamed(") ? "renamed" : "run";
            String qualifiedFunction = qualifiedType + "." + methodName + "()";
            String projectFilePath = String.valueOf(request.options().get("projectFilePath"));

            CodePackage codePackage = new CodePackage();
            codePackage.setId(GraphIds.packageId(packageName));
            codePackage.setName(packageName);
            codePackage.setQualifiedName(packageName);
            codePackage.setPackagePath(packageName.replace('.', '/'));
            fill(codePackage, request, projectFilePath);

            CodeUnit unit = new CodeUnit();
            unit.setId(GraphIds.unitId(qualifiedType));
            unit.setName(typeName);
            unit.setQualifiedName(qualifiedType);
            unit.setUnitType("class");
            unit.setPackageId(codePackage.getId());
            fill(unit, request, projectFilePath);

            CodeFunction function = new CodeFunction();
            function.setId(GraphIds.functionId(qualifiedFunction));
            function.setName(methodName);
            function.setQualifiedName(qualifiedFunction);
            function.setSignature(methodName + "()");
            function.setIsPlaceholder(false);
            fill(function, request, projectFilePath);

            CodeRelationship packageToUnit = relationship(codePackage.getId(), RelationshipType.PACKAGE_TO_UNIT, unit.getId());
            CodeRelationship unitToFunction = relationship(unit.getId(), RelationshipType.UNIT_TO_FUNCTION, function.getId());
            return new GraphDelta(
                new DeltaScope(request.projectName(), language(), request.gitRepoUrl(), request.gitBranch(),
                    request.projectRoot(), request.sourceFiles(), request.changeType(), Map.of()),
                List.of(codePackage), List.of(unit), List.of(function), List.of(),
                List.of(packageToUnit, unitToFunction), List.of(), List.of(), List.of());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot parse test fixture", exception);
        }
    }

    private static void fill(com.poseidon.codegraph.model.CodeNode node, ParseRequest request, String path) {
        node.setLanguage("java");
        node.setProjectName(request.projectName());
        node.setProjectFilePath(path);
        node.setGitRepoUrl(request.gitRepoUrl());
        node.setGitBranch(request.gitBranch());
    }

    private static CodeRelationship relationship(String from, RelationshipType type, String to) {
        CodeRelationship relationship = new CodeRelationship();
        relationship.setId(GraphIds.relationshipId(from, type, to));
        relationship.setFromNodeId(from);
        relationship.setToNodeId(to);
        relationship.setRelationshipType(type);
        relationship.setLanguage("java");
        return relationship;
    }

    private static String match(Pattern pattern, String source, String fallback) {
        var matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
