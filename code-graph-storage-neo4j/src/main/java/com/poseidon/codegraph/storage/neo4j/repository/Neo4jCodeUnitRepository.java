package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
public class Neo4jCodeUnitRepository implements CodeUnitRepository {

    private final Driver neo4jDriver;

    public Neo4jCodeUnitRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public List<CodeUnitDO> findUnitsByProjectFilePath(String projectName, String projectFilePath) {
        String cypher = """
            MATCH (unit:CodeUnit)
            WHERE unit.projectName = $projectName
              AND unit.projectFilePath = $projectFilePath
            RETURN unit
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "projectFilePath", projectFilePath))
                .stream()
                .map(record -> mapToCodeUnitDO(record.get("unit").asMap()))
                .collect(Collectors.toList());
        }
    }

    @Override
    public Set<String> findExistingUnitsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return new HashSet<>();
        }
        
        String cypher = """
            MATCH (u:CodeUnit)
            WHERE u.projectName = $projectName
              AND (u.id IN $qualifiedNames OR u.qualifiedName IN $qualifiedNames)
            RETURN DISTINCT COALESCE(u.id, u.qualifiedName) AS id
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> record.get("id").asString())
                .collect(Collectors.toSet());
        }
    }

    @Override
    public void insertUnitsBatch(List<CodeUnitDO> units) {
        if (units == null || units.isEmpty()) {
            return;
        }
        
        log.debug("批量插入单元开始: count={}", units.size());
        
        String insertCypher = """
            UNWIND $units AS unit
            MERGE (u:CodeUnit {id: unit.id})
            SET u.name = unit.name,
                u.qualifiedName = unit.qualifiedName,
                u.language = unit.language,
                u.projectName = unit.projectName,
                u.projectFilePath = unit.projectFilePath,
                u.gitRepoUrl = unit.gitRepoUrl,
                u.gitBranch = unit.gitBranch,
                u.startLine = unit.startLine,
                u.endLine = unit.endLine,
                u.unitType = unit.unitType,
                u.modifiers = unit.modifiers,
                u.isAbstract = unit.isAbstract,
                u.packageId = unit.packageId
            """;
        
        List<Map<String, Object>> insertParams = units.stream()
            .map(this::unitToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(insertCypher, Values.parameters("units", insertParams));
            log.info("批量插入单元成功: count={}", units.size());
        } catch (Exception e) {
            log.error("批量插入单元失败: count={}, error={}", units.size(), e.getMessage(), e);
            throw new RuntimeException("批量插入单元失败", e);
        }
    }

    @Override
    public void updateUnitsBatch(List<CodeUnitDO> units) {
        if (units == null || units.isEmpty()) {
            return;
        }
        
        log.debug("批量更新单元开始: count={}", units.size());
        
        String updateCypher = """
            UNWIND $units AS unit
            MATCH (u:CodeUnit {id: unit.id, projectName: unit.projectName})
            SET u.name = unit.name,
                u.qualifiedName = unit.qualifiedName,
                u.language = unit.language,
                u.projectName = unit.projectName,
                u.projectFilePath = unit.projectFilePath,
                u.gitRepoUrl = unit.gitRepoUrl,
                u.gitBranch = unit.gitBranch,
                u.startLine = unit.startLine,
                u.endLine = unit.endLine,
                u.unitType = unit.unitType,
                u.modifiers = unit.modifiers,
                u.isAbstract = unit.isAbstract,
                u.packageId = unit.packageId
            """;
        
        List<Map<String, Object>> updateParams = units.stream()
            .map(this::unitToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(updateCypher, Values.parameters("units", updateParams));
            log.info("批量更新单元成功: count={}", units.size());
        } catch (Exception e) {
            log.error("批量更新单元失败: count={}, error={}", units.size(), e.getMessage(), e);
            throw new RuntimeException("批量更新单元失败", e);
        }
    }
    
    @Override
    public void deleteById(String projectName, String id) {
        String cypher = """
            MATCH (n:CodeUnit)
            WHERE n.projectName = $projectName
              AND n.id = $id
            DETACH DELETE n
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("projectName", projectName, "id", id));
            log.debug("删除单元: {}", id);
        }
    }

    private CodeUnitDO mapToCodeUnitDO(Map<String, Object> map) {
        CodeUnitDO unit = new CodeUnitDO();
        unit.setId((String) map.get("id"));
        unit.setName((String) map.get("name"));
        unit.setQualifiedName((String) map.get("qualifiedName"));
        unit.setLanguage((String) map.get("language"));
        unit.setProjectName((String) map.get("projectName"));
        unit.setProjectFilePath((String) map.get("projectFilePath"));
        unit.setGitRepoUrl((String) map.get("gitRepoUrl"));
        unit.setGitBranch((String) map.get("gitBranch"));
        unit.setStartLine(map.get("startLine") != null ? ((Number) map.get("startLine")).intValue() : null);
        unit.setEndLine(map.get("endLine") != null ? ((Number) map.get("endLine")).intValue() : null);
        unit.setUnitType((String) map.get("unitType"));
        unit.setModifiers(map.get("modifiers") != null ? (List<String>) map.get("modifiers") : new ArrayList<>());
        unit.setIsAbstract(map.get("isAbstract") != null ? (Boolean) map.get("isAbstract") : false);
        unit.setPackageId((String) map.get("packageId"));
        return unit;
    }

    private Map<String, Object> unitToMap(CodeUnitDO unit) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", unit.getId());
        map.put("name", unit.getName());
        map.put("qualifiedName", unit.getQualifiedName());
        map.put("language", unit.getLanguage());
        map.put("projectName", unit.getProjectName());
        map.put("projectFilePath", unit.getProjectFilePath());
        map.put("gitRepoUrl", unit.getGitRepoUrl());
        map.put("gitBranch", unit.getGitBranch());
        map.put("startLine", unit.getStartLine());
        map.put("endLine", unit.getEndLine());
        map.put("unitType", unit.getUnitType());
        map.put("modifiers", unit.getModifiers() != null ? unit.getModifiers() : new ArrayList<>());
        map.put("isAbstract", unit.getIsAbstract());
        map.put("packageId", unit.getPackageId());
        return map;
    }
}
