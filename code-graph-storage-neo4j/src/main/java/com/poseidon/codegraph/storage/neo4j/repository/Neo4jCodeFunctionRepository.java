package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
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
public class Neo4jCodeFunctionRepository implements CodeFunctionRepository {

    private final Driver neo4jDriver;

    public Neo4jCodeFunctionRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByProjectFilePath(String projectName, String projectFilePath) {
        String cypher = """
            MATCH (func:CodeFunction)
            WHERE func.projectName = $projectName
              AND func.projectFilePath = $projectFilePath
            RETURN func
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "projectFilePath", projectFilePath))
                .stream()
                .map(record -> mapToCodeFunctionDO(record.get("func").asMap()))
                .collect(Collectors.toList());
        }
    }

    @Override
    public Set<String> findExistingFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return new HashSet<>();
        }
        
        String cypher = """
            MATCH (f:CodeFunction)
            WHERE f.projectName = $projectName
              AND (f.id IN $qualifiedNames OR f.qualifiedName IN $qualifiedNames)
            RETURN DISTINCT COALESCE(f.id, f.qualifiedName) AS id
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> record.get("id").asString())
                .collect(Collectors.toSet());
        }
    }

    @Override
    public List<CodeFunctionDO> findFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return List.of();
        }

        String cypher = """
            MATCH (f:CodeFunction)
            WHERE f.projectName = $projectName
              AND (f.id IN $qualifiedNames OR f.qualifiedName IN $qualifiedNames)
            RETURN f
            """;

        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> mapToCodeFunctionDO(record.get("f").asMap()))
                .collect(Collectors.toList());
        }
    }

    @Override
    public void insertFunctionsBatch(List<CodeFunctionDO> functions) {
        if (functions == null || functions.isEmpty()) {
            return;
        }
        
        log.debug("批量插入函数开始: count={}", functions.size());
        
        String insertCypher = """
            UNWIND $functions AS func
            MERGE (f:CodeFunction {id: func.id})
            SET f.name = func.name,
                f.qualifiedName = func.qualifiedName,
                f.language = func.language,
                f.projectName = func.projectName,
                f.projectFilePath = func.projectFilePath,
                f.gitRepoUrl = func.gitRepoUrl,
                f.gitBranch = func.gitBranch,
                f.startLine = func.startLine,
                f.endLine = func.endLine,
                f.signature = func.signature,
                f.returnType = func.returnType,
                f.modifiers = func.modifiers,
                f.isStatic = func.isStatic,
                f.isAsync = func.isAsync,
                f.isConstructor = func.isConstructor,
                f.isPlaceholder = func.isPlaceholder
            """;
        
        List<Map<String, Object>> insertParams = functions.stream()
            .map(this::functionToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(insertCypher, Values.parameters("functions", insertParams));
            log.info("批量插入函数成功: count={}", functions.size());
        } catch (Exception e) {
            log.error("批量插入函数失败: count={}, error={}", functions.size(), e.getMessage(), e);
            throw new RuntimeException("批量插入函数失败", e);
        }
    }

    @Override
    public void updateFunctionsBatch(List<CodeFunctionDO> functions) {
        if (functions == null || functions.isEmpty()) {
            return;
        }
        
        log.debug("批量更新函数开始: count={}", functions.size());
        
        String updateCypher = """
            UNWIND $functions AS func
            MATCH (f:CodeFunction {id: func.id, projectName: func.projectName})
            SET f.name = func.name,
                f.qualifiedName = func.qualifiedName,
                f.language = func.language,
                f.projectName = func.projectName,
                f.projectFilePath = func.projectFilePath,
                f.gitRepoUrl = func.gitRepoUrl,
                f.gitBranch = func.gitBranch,
                f.startLine = func.startLine,
                f.endLine = func.endLine,
                f.signature = func.signature,
                f.returnType = func.returnType,
                f.modifiers = func.modifiers,
                f.isStatic = func.isStatic,
                f.isAsync = func.isAsync,
                f.isConstructor = func.isConstructor,
                f.isPlaceholder = func.isPlaceholder
            """;
        
        List<Map<String, Object>> updateParams = functions.stream()
            .map(this::functionToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(updateCypher, Values.parameters("functions", updateParams));
            log.info("批量更新函数成功: count={}", functions.size());
        } catch (Exception e) {
            log.error("批量更新函数失败: count={}, error={}", functions.size(), e.getMessage(), e);
            throw new RuntimeException("批量更新函数失败", e);
        }
    }
    
    @Override
    public void deleteById(String projectName, String id) {
        String cypher = """
            MATCH (n)
            WHERE n.projectName = $projectName
              AND n.id = $id
            DETACH DELETE n
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("projectName", projectName, "id", id));
            log.debug("删除节点: {}", id);
        }
    }

    private CodeFunctionDO mapToCodeFunctionDO(Map<String, Object> map) {
        CodeFunctionDO function = new CodeFunctionDO();
        function.setId((String) map.get("id"));
        function.setName((String) map.get("name"));
        function.setQualifiedName((String) map.get("qualifiedName"));
        function.setLanguage((String) map.get("language"));
        function.setProjectName((String) map.get("projectName"));
        function.setProjectFilePath((String) map.get("projectFilePath"));
        function.setGitRepoUrl((String) map.get("gitRepoUrl"));
        function.setGitBranch((String) map.get("gitBranch"));
        function.setStartLine(map.get("startLine") != null ? ((Number) map.get("startLine")).intValue() : null);
        function.setEndLine(map.get("endLine") != null ? ((Number) map.get("endLine")).intValue() : null);
        function.setSignature((String) map.get("signature"));
        function.setReturnType((String) map.get("returnType"));
        function.setModifiers(map.get("modifiers") != null ? (List<String>) map.get("modifiers") : new ArrayList<>());
        function.setIsStatic(map.get("isStatic") != null ? (Boolean) map.get("isStatic") : false);
        function.setIsAsync(map.get("isAsync") != null ? (Boolean) map.get("isAsync") : false);
        function.setIsConstructor(map.get("isConstructor") != null ? (Boolean) map.get("isConstructor") : false);
        function.setIsPlaceholder(map.get("isPlaceholder") != null ? (Boolean) map.get("isPlaceholder") : false);
        return function;
    }

    private Map<String, Object> functionToMap(CodeFunctionDO function) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", function.getId());
        map.put("name", function.getName());
        map.put("qualifiedName", function.getQualifiedName());
        map.put("language", function.getLanguage());
        map.put("projectName", function.getProjectName());
        map.put("projectFilePath", function.getProjectFilePath());
        map.put("gitRepoUrl", function.getGitRepoUrl());
        map.put("gitBranch", function.getGitBranch());
        map.put("startLine", function.getStartLine());
        map.put("endLine", function.getEndLine());
        map.put("signature", function.getSignature());
        map.put("returnType", function.getReturnType());
        map.put("modifiers", function.getModifiers() != null ? function.getModifiers() : new ArrayList<>());
        map.put("isStatic", function.getIsStatic());
        map.put("isAsync", function.getIsAsync());
        map.put("isConstructor", function.getIsConstructor());
        map.put("isPlaceholder", function.getIsPlaceholder() != null ? function.getIsPlaceholder() : false);
        return map;
    }
}
