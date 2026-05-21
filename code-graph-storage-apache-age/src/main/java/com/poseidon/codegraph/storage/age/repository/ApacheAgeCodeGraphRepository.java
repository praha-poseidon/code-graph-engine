package com.poseidon.codegraph.storage.age.repository;

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
import com.poseidon.codegraph.model.RelationshipType;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ApacheAgeCodeGraphRepository implements
        CodePackageRepository,
        CodeUnitRepository,
        CodeFunctionRepository,
        CodeEndpointRepository,
        CodeRelationshipRepository {

    private final ApacheAgeCypher age;

    public ApacheAgeCodeGraphRepository(ApacheAgeCypher age) {
        this.age = age;
    }

    @Override
    public Set<String> findExistingPackagesByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return existingIds("CodePackage", projectName, qualifiedNames);
    }

    @Override
    public void insertPackagesBatch(List<CodePackageDO> packages) {
        safe(packages).forEach(pkg -> mergeNode("CodePackage", pkg.getId(), packageProps(pkg)));
    }

    @Override
    public void updatePackagesBatch(List<CodePackageDO> packages) {
        insertPackagesBatch(packages);
    }

    @Override
    public List<CodeUnitDO> findUnitsByProjectFilePath(String projectName, String projectFilePath) {
        return findNodeIdsByFile("CodeUnit", projectName, projectFilePath).stream()
            .map(id -> {
                CodeUnitDO unit = new CodeUnitDO();
                unit.setId(id);
                unit.setProjectName(projectName);
                unit.setProjectFilePath(projectFilePath);
                return unit;
            })
            .toList();
    }

    @Override
    public Set<String> findExistingUnitsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return existingIds("CodeUnit", projectName, qualifiedNames);
    }

    @Override
    public void insertUnitsBatch(List<CodeUnitDO> units) {
        safe(units).forEach(unit -> mergeNode("CodeUnit", unit.getId(), unitProps(unit)));
    }

    @Override
    public void updateUnitsBatch(List<CodeUnitDO> units) {
        insertUnitsBatch(units);
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByProjectFilePath(String projectName, String projectFilePath) {
        return age.query("""
            MATCH (n:CodeFunction)
            WHERE n.projectName = %s AND n.projectFilePath = %s
            RETURN n.id
            """.formatted(age.value(projectName), age.value(projectFilePath))).stream()
            .map(row -> minimalFunction(text(row), projectName, projectFilePath))
            .toList();
    }

    @Override
    public Set<String> findExistingFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        return existingIds("CodeFunction", projectName, qualifiedNames);
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return List.of();
        }
        return existingIds("CodeFunction", projectName, qualifiedNames).stream()
            .map(id -> minimalFunction(id, projectName, null))
            .toList();
    }

    @Override
    public void insertFunctionsBatch(List<CodeFunctionDO> functions) {
        safe(functions).forEach(function -> mergeNode("CodeFunction", function.getId(), functionProps(function)));
    }

    @Override
    public void updateFunctionsBatch(List<CodeFunctionDO> functions) {
        insertFunctionsBatch(functions);
    }

    @Override
    public void insertEndpointsBatch(List<CodeEndpointDO> endpoints) {
        safe(endpoints).forEach(endpoint -> mergeNode("CodeEndpoint", endpoint.getId(), endpointProps(endpoint)));
    }

    @Override
    public void updateEndpointsBatch(List<CodeEndpointDO> endpoints) {
        insertEndpointsBatch(endpoints);
    }

    @Override
    public void deleteById(String projectName, String id) {
        age.execute("""
            MATCH (n)
            WHERE n.projectName = %s AND n.id = %s
            DETACH DELETE n
            """.formatted(age.value(projectName), age.value(id)));
        age.execute("""
            MATCH ()-[r]->()
            WHERE r.projectName = %s AND r.id = %s
            DELETE r
            """.formatted(age.value(projectName), age.value(id)));
    }

    @Override
    public Set<String> findExistingEndpointsByIds(String projectName, List<String> ids) {
        return existingIds("CodeEndpoint", projectName, ids);
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByProjectFilePath(String projectName, String projectFilePath) {
        return findNodeIdsByFile("CodeEndpoint", projectName, projectFilePath).stream()
            .map(id -> {
                CodeEndpointDO endpoint = new CodeEndpointDO();
                endpoint.setId(id);
                endpoint.setProjectName(projectName);
                endpoint.setProjectFilePath(projectFilePath);
                return endpoint;
            })
            .toList();
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByDirection(String projectName, String direction) {
        return age.query("""
            MATCH (n:CodeEndpoint)
            WHERE n.projectName = %s AND n.direction = %s
            RETURN n.id
            """.formatted(age.value(projectName), age.value(direction))).stream()
            .map(row -> minimalEndpoint(text(row), projectName, direction, null))
            .toList();
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByMatchIdentity(String matchIdentity, String direction) {
        String condition = direction == null || direction.isBlank()
            ? "n.matchIdentity = " + age.value(matchIdentity)
            : "n.matchIdentity = " + age.value(matchIdentity) + " AND n.direction = " + age.value(direction);
        return age.query("MATCH (n:CodeEndpoint) WHERE " + condition + " RETURN n.id").stream()
            .map(row -> minimalEndpoint(text(row), null, direction, matchIdentity))
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
        return age.query("""
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE caller.projectName = %s AND callee.projectName = %s AND callee.projectFilePath = %s
            RETURN caller.projectFilePath
            """.formatted(age.value(projectName), age.value(projectName), age.value(targetProjectFilePath))).stream()
            .map(row -> {
                FileMetaInfo meta = new FileMetaInfo();
                meta.setProjectFilePath(text(row));
                return meta;
            })
            .toList();
    }

    @Override
    public void deleteFileOutgoingCalls(String projectName, String projectFilePath) {
        age.execute("""
            MATCH (caller:CodeFunction)-[r:CALLS]->()
            WHERE caller.projectName = %s AND caller.projectFilePath = %s
            DELETE r
            """.formatted(age.value(projectName), age.value(projectFilePath)));
    }

    @Override
    public void insertRelationshipsBatch(List<CodeRelationshipDO> relationships) {
        safe(relationships).forEach(this::mergeRelationship);
    }

    @Override
    public List<CodeRelationshipDO> findOutgoingRelationships(String projectName, String nodeId, String relationshipType) {
        String type = relationshipType == null || relationshipType.isBlank() ? "" : ":" + relationshipType;
        return age.query("""
            MATCH (from {id: %s})-[r%s]->()
            WHERE from.projectName = %s AND r.projectName = %s
            RETURN r.id
            """.formatted(age.value(nodeId), type, age.value(projectName), age.value(projectName))).stream()
            .map(row -> minimalRelationship(text(row), projectName, nodeId, null, relationshipType))
            .toList();
    }

    @Override
    public List<CodeRelationshipDO> findIncomingRelationships(String projectName, String nodeId, String relationshipType) {
        String type = relationshipType == null || relationshipType.isBlank() ? "" : ":" + relationshipType;
        return age.query("""
            MATCH ()-[r%s]->(to {id: %s})
            WHERE to.projectName = %s AND r.projectName = %s
            RETURN r.id
            """.formatted(type, age.value(nodeId), age.value(projectName), age.value(projectName))).stream()
            .map(row -> minimalRelationship(text(row), projectName, null, nodeId, relationshipType))
            .toList();
    }

    @Override
    public Set<String> findExistingStructureRelationships(String projectName, List<CodeRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new LinkedHashSet<>();
        for (CodeRelationshipDO relationship : relationships) {
            List<Map<String, Object>> rows = age.query("""
                MATCH (from {id: %s})-[r:%s]->(to {id: %s})
                WHERE from.projectName = %s
                  AND to.projectName = %s
                  AND r.projectName = %s
                RETURN r.id
                """.formatted(
                    age.value(relationship.getFromNodeId()),
                    relationship.getRelationshipType(),
                    age.value(relationship.getToNodeId()),
                    age.value(projectName),
                    age.value(projectName),
                    age.value(projectName)));
            if (!rows.isEmpty()) {
                existing.add(relationshipKey(relationship));
            }
        }
        return existing;
    }

    private void mergeNode(String label, String id, Map<String, ?> props) {
        age.execute("""
            MERGE (n:%s {id: %s})
            SET n += %s
            RETURN n
            """.formatted(label, age.value(id), age.props(props)));
    }

    private void mergeRelationship(CodeRelationshipDO relationship) {
        RelationshipType type = RelationshipType.valueOf(relationship.getRelationshipType());
        age.execute("""
            MATCH (from:%s {id: %s, projectName: %s})
            MATCH (to:%s {id: %s, projectName: %s})
            MERGE (from)-[r:%s {id: %s}]->(to)
            SET r += %s
            RETURN r
            """.formatted(
            type.getFromLabel(),
            age.value(relationship.getFromNodeId()),
            age.value(relationship.getProjectName()),
            type.getToLabel(),
            age.value(relationship.getToNodeId()),
            age.value(relationship.getProjectName()),
            relationship.getRelationshipType(),
            age.value(relationship.getId()),
            age.props(relationshipProps(relationship))));
    }

    private Set<String> existingIds(String label, String projectName, List<String> idsOrNames) {
        if (idsOrNames == null || idsOrNames.isEmpty()) {
            return Set.of();
        }
        return age.query("""
            MATCH (n:%s)
            WHERE n.projectName = %s AND (n.id IN %s OR n.qualifiedName IN %s)
            RETURN n.id
            """.formatted(label, age.value(projectName), age.stringList(idsOrNames), age.stringList(idsOrNames)))
            .stream()
            .map(this::text)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> findNodeIdsByFile(String label, String projectName, String projectFilePath) {
        return age.query("""
            MATCH (n:%s)
            WHERE n.projectName = %s AND n.projectFilePath = %s
            RETURN n.id
            """.formatted(label, age.value(projectName), age.value(projectFilePath)))
            .stream()
            .map(this::text)
            .toList();
    }

    private Map<String, ?> packageProps(CodePackageDO pkg) {
        return Map.of(
            "id", pkg.getId(),
            "name", value(pkg.getName()),
            "qualifiedName", value(pkg.getQualifiedName()),
            "language", value(pkg.getLanguage()),
            "projectName", value(pkg.getProjectName()),
            "projectFilePath", value(pkg.getProjectFilePath()),
            "packagePath", value(pkg.getPackagePath()));
    }

    private Map<String, ?> unitProps(CodeUnitDO unit) {
        return Map.of(
            "id", unit.getId(),
            "name", value(unit.getName()),
            "qualifiedName", value(unit.getQualifiedName()),
            "language", value(unit.getLanguage()),
            "projectName", value(unit.getProjectName()),
            "projectFilePath", value(unit.getProjectFilePath()),
            "unitType", value(unit.getUnitType()),
            "packageId", value(unit.getPackageId()));
    }

    private Map<String, ?> functionProps(CodeFunctionDO function) {
        return Map.of(
            "id", function.getId(),
            "name", value(function.getName()),
            "qualifiedName", value(function.getQualifiedName()),
            "language", value(function.getLanguage()),
            "projectName", value(function.getProjectName()),
            "projectFilePath", value(function.getProjectFilePath()),
            "signature", value(function.getSignature()),
            "returnType", value(function.getReturnType()),
            "isPlaceholder", value(function.getIsPlaceholder()));
    }

    private Map<String, ?> endpointProps(CodeEndpointDO endpoint) {
        return Map.ofEntries(
            Map.entry("id", endpoint.getId()),
            Map.entry("name", value(endpoint.getName())),
            Map.entry("qualifiedName", value(endpoint.getQualifiedName())),
            Map.entry("language", value(endpoint.getLanguage())),
            Map.entry("projectName", value(endpoint.getProjectName())),
            Map.entry("projectFilePath", value(endpoint.getProjectFilePath())),
            Map.entry("endpointType", value(endpoint.getEndpointType())),
            Map.entry("direction", value(endpoint.getDirection())),
            Map.entry("httpMethod", value(endpoint.getHttpMethod())),
            Map.entry("path", value(endpoint.getPath())),
            Map.entry("normalizedPath", value(endpoint.getNormalizedPath())),
            Map.entry("matchIdentity", value(endpoint.getMatchIdentity())));
    }

    private Map<String, ?> relationshipProps(CodeRelationshipDO relationship) {
        return Map.of(
            "id", relationship.getId(),
            "fromNodeId", relationship.getFromNodeId(),
            "toNodeId", relationship.getToNodeId(),
            "relationshipType", relationship.getRelationshipType(),
            "projectName", relationship.getProjectName(),
            "language", value(relationship.getLanguage()));
    }

    private Object value(Object value) {
        return value == null ? "" : value;
    }

    private String text(Map<String, Object> row) {
        Object value = row.values().stream().findFirst().orElse("");
        String text = String.valueOf(value);
        return text.replaceAll("^\"|\"$", "");
    }

    private CodeFunctionDO minimalFunction(String id, String projectName, String projectFilePath) {
        CodeFunctionDO function = new CodeFunctionDO();
        function.setId(id);
        function.setProjectName(projectName);
        function.setProjectFilePath(projectFilePath);
        function.setIsPlaceholder(false);
        return function;
    }

    private CodeEndpointDO minimalEndpoint(String id, String projectName, String direction, String matchIdentity) {
        CodeEndpointDO endpoint = new CodeEndpointDO();
        endpoint.setId(id);
        endpoint.setProjectName(projectName);
        endpoint.setDirection(direction);
        endpoint.setMatchIdentity(matchIdentity);
        return endpoint;
    }

    private CodeRelationshipDO minimalRelationship(String id, String projectName, String from, String to, String type) {
        CodeRelationshipDO relationship = new CodeRelationshipDO();
        relationship.setId(id);
        relationship.setProjectName(projectName);
        relationship.setFromNodeId(from);
        relationship.setToNodeId(to);
        relationship.setRelationshipType(type);
        return relationship;
    }

    private String relationshipKey(CodeRelationshipDO relationship) {
        return relationship.getFromNodeId() + ":" + relationship.getToNodeId() + ":" + relationship.getRelationshipType();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
