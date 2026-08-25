package com.poseidon.codegraph.storage.memory.repository;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodePackageDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.engine.application.model.FileMetaInfo;
import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory storage backend for local demos, tests, and quick starts.
 */
@Repository
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryCodeGraphRepository implements
        CodePackageRepository,
        CodeUnitRepository,
        CodeFunctionRepository,
        CodeEndpointRepository,
        CodeRelationshipRepository {

    private final Map<String, CodePackageDO> packages = new ConcurrentHashMap<>();
    private final Map<String, CodeUnitDO> units = new ConcurrentHashMap<>();
    private final Map<String, CodeFunctionDO> functions = new ConcurrentHashMap<>();
    private final Map<String, CodeEndpointDO> endpoints = new ConcurrentHashMap<>();
    private final Map<String, CodeRelationshipDO> relationships = new ConcurrentHashMap<>();

    public List<CodePackageDO> findPackagesByProject(String projectName) {
        return packages.values().stream()
            .filter(pkg -> sameProject(projectName, pkg.getProjectName()))
            .toList();
    }

    public List<CodePackageDO> findAllPackages() {
        return new ArrayList<>(packages.values());
    }

    public List<CodeUnitDO> findUnitsByProject(String projectName) {
        return units.values().stream()
            .filter(unit -> sameProject(projectName, unit.getProjectName()))
            .toList();
    }

    public List<CodeUnitDO> findAllUnits() {
        return new ArrayList<>(units.values());
    }

    public List<CodeFunctionDO> findFunctionsByProject(String projectName) {
        return functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .toList();
    }

    public List<CodeFunctionDO> findAllFunctions() {
        return new ArrayList<>(functions.values());
    }

    public List<CodeEndpointDO> findEndpointsByProject(String projectName) {
        return endpoints.values().stream()
            .filter(endpoint -> sameProject(projectName, endpoint.getProjectName()))
            .toList();
    }

    public List<CodeEndpointDO> findAllEndpoints() {
        return new ArrayList<>(endpoints.values());
    }

    public List<CodeRelationshipDO> findRelationshipsByProject(String projectName) {
        return relationships.values().stream()
            .filter(relationship -> sameProject(projectName, relationship.getProjectName()))
            .toList();
    }

    public List<CodeRelationshipDO> findAllRelationships() {
        return new ArrayList<>(relationships.values());
    }

    @Override
    public Set<String> findExistingPackagesByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return packages.values().stream()
            .filter(pkg -> sameProject(projectName, pkg.getProjectName()))
            .filter(pkg -> contains(qualifiedNames, pkg.getId()) || contains(qualifiedNames, pkg.getQualifiedName()))
            .map(CodePackageDO::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void insertPackagesBatch(List<CodePackageDO> packages) {
        safeList(packages).forEach(pkg -> this.packages.put(pkg.getId(), pkg));
    }

    @Override
    public void updatePackagesBatch(List<CodePackageDO> packages) {
        insertPackagesBatch(packages);
    }

    @Override
    public List<CodeUnitDO> findUnitsByProjectFilePath(String projectName, String projectFilePath) {
        return units.values().stream()
            .filter(unit -> sameProject(projectName, unit.getProjectName()))
            .filter(unit -> Objects.equals(projectFilePath, unit.getProjectFilePath()))
            .toList();
    }

    @Override
    public Set<String> findExistingUnitsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return units.values().stream()
            .filter(unit -> sameProject(projectName, unit.getProjectName()))
            .filter(unit -> contains(qualifiedNames, unit.getId()) || contains(qualifiedNames, unit.getQualifiedName()))
            .map(CodeUnitDO::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void insertUnitsBatch(List<CodeUnitDO> units) {
        safeList(units).forEach(unit -> this.units.put(unit.getId(), unit));
    }

    @Override
    public void updateUnitsBatch(List<CodeUnitDO> units) {
        insertUnitsBatch(units);
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByProjectFilePath(String projectName, String projectFilePath) {
        return functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .filter(function -> Objects.equals(projectFilePath, function.getProjectFilePath()))
            .toList();
    }

    @Override
    public Set<String> findExistingFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .filter(function -> contains(qualifiedNames, function.getId()) || contains(qualifiedNames, function.getQualifiedName()))
            .map(CodeFunctionDO::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .filter(function -> contains(qualifiedNames, function.getId()) || contains(qualifiedNames, function.getQualifiedName()))
            .toList();
    }

    @Override
    public void insertFunctionsBatch(List<CodeFunctionDO> functions) {
        safeList(functions).forEach(function -> this.functions.put(function.getId(), function));
    }

    @Override
    public void updateFunctionsBatch(List<CodeFunctionDO> functions) {
        insertFunctionsBatch(functions);
    }

    @Override
    public void insertEndpointsBatch(List<CodeEndpointDO> endpoints) {
        safeList(endpoints).forEach(endpoint -> this.endpoints.put(endpoint.getId(), endpoint));
    }

    @Override
    public void updateEndpointsBatch(List<CodeEndpointDO> endpoints) {
        insertEndpointsBatch(endpoints);
    }

    @Override
    public Set<String> findExistingEndpointsByIds(String projectName, List<String> ids) {
        return endpoints.values().stream()
            .filter(endpoint -> sameProject(projectName, endpoint.getProjectName()))
            .filter(endpoint -> contains(ids, endpoint.getId()))
            .map(CodeEndpointDO::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByProjectFilePath(String projectName, String projectFilePath) {
        return endpoints.values().stream()
            .filter(endpoint -> sameProject(projectName, endpoint.getProjectName()))
            .filter(endpoint -> Objects.equals(projectFilePath, endpoint.getProjectFilePath()))
            .toList();
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByDirection(String projectName, String direction) {
        return endpoints.values().stream()
            .filter(endpoint -> sameProject(projectName, endpoint.getProjectName()))
            .filter(endpoint -> Objects.equals(direction, endpoint.getDirection()))
            .toList();
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByMatchIdentity(String matchIdentity, String direction) {
        return endpoints.values().stream()
            .filter(endpoint -> Objects.equals(matchIdentity, endpoint.getMatchIdentity()))
            .filter(endpoint -> direction == null || direction.isBlank() || Objects.equals(direction, endpoint.getDirection()))
            .toList();
    }

    @Override
    public List<String> findWhoCallsMe(String projectName, String targetProjectFilePath) {
        return findWhoCallsMeWithMeta(projectName, targetProjectFilePath).stream()
            .map(FileMetaInfo::getProjectFilePath)
            .distinct()
            .toList();
    }

    @Override
    public List<FileMetaInfo> findWhoCallsMeWithMeta(String projectName, String targetProjectFilePath) {
        Set<String> calleeIds = functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .filter(function -> Objects.equals(targetProjectFilePath, function.getProjectFilePath()))
            .map(CodeFunctionDO::getId)
            .collect(Collectors.toSet());
        return relationships.values().stream()
            .filter(relationship -> sameProject(projectName, relationship.getProjectName()))
            .filter(relationship -> "CALLS".equals(relationship.getRelationshipType()))
            .filter(relationship -> calleeIds.contains(relationship.getToNodeId()))
            .map(relationship -> functions.get(relationship.getFromNodeId()))
            .filter(Objects::nonNull)
            .map(this::toFileMetaInfo)
            .distinct()
            .toList();
    }

    @Override
    public void deleteFileOutgoingCalls(String projectName, String projectFilePath) {
        Set<String> callerIds = functions.values().stream()
            .filter(function -> sameProject(projectName, function.getProjectName()))
            .filter(function -> Objects.equals(projectFilePath, function.getProjectFilePath()))
            .map(CodeFunctionDO::getId)
            .collect(Collectors.toSet());
        relationships.values().removeIf(relationship ->
            sameProject(projectName, relationship.getProjectName())
                && "CALLS".equals(relationship.getRelationshipType())
                && callerIds.contains(relationship.getFromNodeId()));
    }

    @Override
    public void deleteById(String projectName, String id) {
        CodeRelationshipDO removedRelationship = relationships.get(id);
        if (removedRelationship != null && sameProject(projectName, removedRelationship.getProjectName())) {
            relationships.remove(id);
        }
        CodeUnitDO removedUnit = units.get(id);
        if (removedUnit != null && sameProject(projectName, removedUnit.getProjectName())) {
            units.remove(id);
            deleteRelationshipsConnectedTo(projectName, id);
        }
        CodeFunctionDO removedFunction = functions.get(id);
        if (removedFunction != null && sameProject(projectName, removedFunction.getProjectName())) {
            functions.remove(id);
            deleteRelationshipsConnectedTo(projectName, id);
        }
        CodeEndpointDO removedEndpoint = endpoints.get(id);
        if (removedEndpoint != null && sameProject(projectName, removedEndpoint.getProjectName())) {
            endpoints.remove(id);
            deleteRelationshipsConnectedTo(projectName, id);
        }
    }

    @Override
    public void insertRelationshipsBatch(List<CodeRelationshipDO> relationships) {
        safeList(relationships).forEach(relationship -> this.relationships.put(relationship.getId(), relationship));
    }

    @Override
    public List<CodeRelationshipDO> findOutgoingRelationships(String projectName, String nodeId, String relationshipType) {
        return relationships.values().stream()
            .filter(relationship -> sameProject(projectName, relationship.getProjectName()))
            .filter(relationship -> Objects.equals(nodeId, relationship.getFromNodeId()))
            .filter(relationship -> relationshipType == null || relationshipType.isBlank()
                || Objects.equals(relationshipType, relationship.getRelationshipType()))
            .toList();
    }

    @Override
    public List<CodeRelationshipDO> findIncomingRelationships(String projectName, String nodeId, String relationshipType) {
        return relationships.values().stream()
            .filter(relationship -> sameProject(projectName, relationship.getProjectName()))
            .filter(relationship -> Objects.equals(nodeId, relationship.getToNodeId()))
            .filter(relationship -> relationshipType == null || relationshipType.isBlank()
                || Objects.equals(relationshipType, relationship.getRelationshipType()))
            .toList();
    }

    @Override
    public Set<String> findExistingStructureRelationships(String projectName, List<CodeRelationshipDO> relationships) {
        Set<String> existing = this.relationships.values().stream()
            .filter(relationship -> sameProject(projectName, relationship.getProjectName()))
            .map(this::relationshipKey)
            .collect(Collectors.toSet());
        return safeList(relationships).stream()
            .map(this::relationshipKey)
            .filter(existing::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void deleteRelationshipsConnectedTo(String projectName, String nodeId) {
        relationships.values().removeIf(relationship ->
            sameProject(projectName, relationship.getProjectName())
                && (Objects.equals(nodeId, relationship.getFromNodeId())
                || Objects.equals(nodeId, relationship.getToNodeId())));
    }

    private String relationshipKey(CodeRelationshipDO relationship) {
        return relationship.getFromNodeId() + ":" + relationship.getToNodeId() + ":" + relationship.getRelationshipType();
    }

    private FileMetaInfo toFileMetaInfo(CodeFunctionDO function) {
        FileMetaInfo meta = new FileMetaInfo();
        meta.setProjectFilePath(function.getProjectFilePath());
        meta.setGitRepoUrl(function.getGitRepoUrl());
        meta.setGitBranch(function.getGitBranch());
        return meta;
    }

    private boolean sameProject(String expected, String actual) {
        return Objects.equals(expected, actual);
    }

    private boolean contains(List<String> values, String value) {
        return values != null && values.contains(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
