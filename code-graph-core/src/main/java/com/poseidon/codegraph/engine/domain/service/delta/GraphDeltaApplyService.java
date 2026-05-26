package com.poseidon.codegraph.engine.domain.service.delta;

import com.poseidon.codegraph.engine.application.converter.CodeGraphConverter;
import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.service.merge.CodeFunctionSavePlan;
import com.poseidon.codegraph.engine.domain.service.merge.CodeFunctionSavePlanner;
import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.model.delta.Diagnostic;
import com.poseidon.codegraph.model.delta.DiagnosticLevel;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.GraphDeltaValidator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies parser output to the graph domain.
 * This class owns merge rules such as placeholder protection and relationship de-duplication.
 */
@Slf4j
public class GraphDeltaApplyService {

    private final CodeFunctionSavePlanner functionSavePlanner = new CodeFunctionSavePlanner();
    private final GraphDeltaValidator validator = new GraphDeltaValidator();
    private final GraphDeltaProjectScopeNormalizer projectScopeNormalizer = new GraphDeltaProjectScopeNormalizer();

    public void apply(GraphDelta delta, CodeGraphContext context) {
        if (delta == null) {
            return;
        }
        delta = projectScopeNormalizer.normalize(delta, context.getProjectName());
        delta = deduplicateEndpoints(delta);
        validator.validateOrThrow(delta);
        logDiagnostics(delta);
        applyDeletes(delta, context);

        List<CodePackage> packages = safeList(delta.packages());
        if (!packages.isEmpty()) {
            savePackagesWithCheck(packages, context);
        }

        List<CodeUnit> units = safeList(delta.units());
        if (!units.isEmpty()) {
            saveUnitsWithCheck(units, context);
        }

        List<CodeFunction> functions = safeList(delta.functions());
        if (!functions.isEmpty()) {
            saveFunctionsWithCheck(functions, context);
        }

        List<CodeEndpoint> endpoints = safeList(delta.endpoints());
        if (!endpoints.isEmpty()) {
            saveEndpointsWithCheck(endpoints, context);
            createEndpointMatchRelationships(endpoints, context);
        }

        List<CodeRelationship> structureRelationships = safeList(delta.relationships()).stream()
            .filter(rel -> rel.getRelationshipType() != RelationshipType.CALLS
                && rel.getRelationshipType() != RelationshipType.MATCHES)
            .collect(Collectors.toList());

        if (!structureRelationships.isEmpty()) {
            saveStructureRelationshipsWithCheck(structureRelationships, context);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private GraphDelta deduplicateEndpoints(GraphDelta delta) {
        List<CodeEndpoint> endpoints = safeList(delta.endpoints());
        if (endpoints.size() < 2) {
            return delta;
        }

        Map<String, CodeEndpoint> uniqueEndpoints = new LinkedHashMap<>();
        for (CodeEndpoint endpoint : endpoints) {
            if (endpoint == null || endpoint.getId() == null || endpoint.getId().isBlank()) {
                uniqueEndpoints.put("invalid:" + uniqueEndpoints.size(), endpoint);
            } else {
                uniqueEndpoints.putIfAbsent(endpoint.getId(), endpoint);
            }
        }

        if (uniqueEndpoints.size() == endpoints.size()) {
            return delta;
        }

        log.info("端点预去重：原始 {} 个，去重后 {}", endpoints.size(), uniqueEndpoints.size());
        return new GraphDelta(
            delta.scope(),
            delta.packages(),
            delta.units(),
            delta.functions(),
            new ArrayList<>(uniqueEndpoints.values()),
            delta.relationships(),
            delta.deletedNodeIds(),
            delta.deletedRelationshipIds(),
            delta.diagnostics());
    }

    private void logDiagnostics(GraphDelta delta) {
        for (Diagnostic diagnostic : safeList(delta.diagnostics())) {
            if (diagnostic == null) {
                continue;
            }
            if (diagnostic.level() == DiagnosticLevel.ERROR) {
                log.error("Parser diagnostic: code={}, file={}, line={}, message={}",
                    diagnostic.code(), diagnostic.projectFilePath(), diagnostic.lineNumber(), diagnostic.message());
            } else if (diagnostic.level() == DiagnosticLevel.WARN) {
                log.warn("Parser diagnostic: code={}, file={}, line={}, message={}",
                    diagnostic.code(), diagnostic.projectFilePath(), diagnostic.lineNumber(), diagnostic.message());
            } else {
                log.info("Parser diagnostic: code={}, file={}, line={}, message={}",
                    diagnostic.code(), diagnostic.projectFilePath(), diagnostic.lineNumber(), diagnostic.message());
            }
        }
    }

    private void applyDeletes(GraphDelta delta, CodeGraphContext context) {
        if (context.getWriter().getDeleteRelationship() != null) {
            for (String relationshipId : safeList(delta.deletedRelationshipIds())) {
                context.getWriter().getDeleteRelationship().accept(relationshipId);
            }
        }
        if (context.getWriter().getDeleteNode() != null) {
            for (String nodeId : safeList(delta.deletedNodeIds())) {
                context.getWriter().getDeleteNode().accept(nodeId);
            }
        }
    }

    private void savePackagesWithCheck(List<CodePackage> packages, CodeGraphContext context) {
        List<String> packageIds = packages.stream()
            .map(CodePackage::getId)
            .collect(Collectors.toList());
        Set<String> existingIds = context.getReader()
            .getFindExistingPackagesByQualifiedNames()
            .apply(packageIds);

        List<CodePackage> toInsert = new ArrayList<>();
        List<CodePackage> toUpdate = new ArrayList<>();

        for (CodePackage pkg : packages) {
            if (existingIds.contains(pkg.getId())) {
                toUpdate.add(pkg);
            } else {
                toInsert.add(pkg);
            }
        }

        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertPackagesBatch().accept(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            context.getWriter().getUpdatePackagesBatch().accept(toUpdate);
        }
    }

    private void saveUnitsWithCheck(List<CodeUnit> units, CodeGraphContext context) {
        List<String> unitIds = units.stream()
            .map(CodeUnit::getId)
            .collect(Collectors.toList());
        Set<String> existingIds = context.getReader()
            .getFindExistingUnitsByQualifiedNames()
            .apply(unitIds);

        List<CodeUnit> toInsert = new ArrayList<>();
        List<CodeUnit> toUpdate = new ArrayList<>();

        for (CodeUnit unit : units) {
            if (existingIds.contains(unit.getId())) {
                toUpdate.add(unit);
            } else {
                toInsert.add(unit);
            }
        }

        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertUnitsBatch().accept(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            context.getWriter().getUpdateUnitsBatch().accept(toUpdate);
        }
    }

    private void saveFunctionsWithCheck(List<CodeFunction> functions, CodeGraphContext context) {
        List<String> functionIds = functions.stream()
            .map(CodeFunction::getId)
            .collect(Collectors.toList());

        Map<String, CodeFunction> existingFunctions = context.getReader()
            .getFindFunctionsByQualifiedNames()
            .apply(functionIds);

        CodeFunctionSavePlan plan = functionSavePlanner.plan(functions, existingFunctions);

        if (!plan.toInsert().isEmpty()) {
            context.getWriter().getInsertFunctionsBatch().accept(plan.toInsert());
        }
        if (!plan.toUpdate().isEmpty()) {
            context.getWriter().getUpdateFunctionsBatch().accept(plan.toUpdate());
        }
        if (!plan.skipped().isEmpty()) {
            log.debug("跳过占位符函数覆盖真实函数: count={}", plan.skipped().size());
        }
    }

    private void saveEndpointsWithCheck(List<CodeEndpoint> endpoints, CodeGraphContext context) {
        Map<String, CodeEndpoint> uniqueEndpoints = new LinkedHashMap<>();
        for (CodeEndpoint endpoint : endpoints) {
            uniqueEndpoints.putIfAbsent(endpoint.getId(), endpoint);
        }

        List<CodeEndpoint> deduplicatedEndpoints = new ArrayList<>(uniqueEndpoints.values());
        log.info("端点去重：原始 {} 个，去重后 {}", endpoints.size(), deduplicatedEndpoints.size());

        List<String> endpointIds = deduplicatedEndpoints.stream()
            .map(CodeEndpoint::getId)
            .collect(Collectors.toList());

        Set<String> existingIds = context.getReader()
            .getFindExistingEndpointsByIds()
            .apply(endpointIds);

        List<CodeEndpoint> toInsert = deduplicatedEndpoints.stream()
            .filter(e -> !existingIds.contains(e.getId()))
            .collect(Collectors.toList());

        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertEndpointsBatch().accept(toInsert);
        }

        log.info("端点保存完成：去重后 {} 个，新插入 {} 个，已存在 {} 个（跳过）",
            deduplicatedEndpoints.size(), toInsert.size(), existingIds.size());
    }

    private void createEndpointMatchRelationships(List<CodeEndpoint> endpoints, CodeGraphContext context) {
        if (endpoints.isEmpty()) {
            return;
        }

        log.info("开始创建端点匹配关系，共 {} 个端点", endpoints.size());
        List<CodeRelationship> matchRelationships = new ArrayList<>();

        for (CodeEndpoint endpoint : endpoints) {
            if (endpoint.getMatchIdentity() == null || endpoint.getMatchIdentity().isEmpty()) {
                log.debug("端点没有 matchIdentity，跳过: {}", endpoint.getId());
                continue;
            }

            String targetDirection = "inbound".equals(endpoint.getDirection()) ? "outbound" : "inbound";
            List<CodeEndpoint> matchingEndpoints = context.getReader()
                .getFindEndpointsByMatchIdentity()
                .apply(endpoint.getMatchIdentity(), targetDirection);

            for (CodeEndpoint matchingEndpoint : matchingEndpoints) {
                CodeRelationship rel = new CodeRelationship();
                rel.setRelationshipType(RelationshipType.MATCHES);

                if ("outbound".equals(endpoint.getDirection())) {
                    rel.setFromNodeId(endpoint.getId());
                    rel.setToNodeId(matchingEndpoint.getId());
                } else {
                    rel.setFromNodeId(matchingEndpoint.getId());
                    rel.setToNodeId(endpoint.getId());
                }

                rel.setId(GraphIds.scoped(
                    context.getProjectName(),
                    GraphIds.relationshipId(rel.getFromNodeId(), RelationshipType.MATCHES, rel.getToNodeId())));
                rel.setLanguage(endpoint.getLanguage());
                rel.setProjectName(context.getProjectName());
                matchRelationships.add(rel);
            }
        }

        if (!matchRelationships.isEmpty()) {
            context.getWriter().getInsertRelationshipsBatch().accept(matchRelationships);
            log.info("端点匹配关系创建完成: count={}", matchRelationships.size());
        }
    }

    private void saveStructureRelationshipsWithCheck(List<CodeRelationship> relationships, CodeGraphContext context) {
        if (relationships.isEmpty()) {
            return;
        }

        List<com.poseidon.codegraph.engine.application.model.CodeRelationshipDO> relationshipDOs = relationships.stream()
            .map(CodeGraphConverter::toDO)
            .collect(Collectors.toList());

        Set<String> existingKeys = context.getReader().getFindExistingStructureRelationships() != null
            ? context.getReader().getFindExistingStructureRelationships().apply(relationshipDOs)
            : Set.of();

        List<CodeRelationship> toInsert = new ArrayList<>();
        for (CodeRelationship rel : relationships) {
            String key = rel.getFromNodeId() + ":" + rel.getToNodeId() + ":" + rel.getRelationshipType();
            if (!existingKeys.contains(key)) {
                toInsert.add(rel);
            }
        }

        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertRelationshipsBatch().accept(toInsert);
            log.info("批量插入结构关系: count={}", toInsert.size());
        }
    }
}
