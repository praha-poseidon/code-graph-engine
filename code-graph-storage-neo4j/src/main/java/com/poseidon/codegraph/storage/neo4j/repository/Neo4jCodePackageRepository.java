package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodePackageDO;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
public class Neo4jCodePackageRepository implements CodePackageRepository {

    private final Driver neo4jDriver;

    public Neo4jCodePackageRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public Set<String> findExistingPackagesByQualifiedNames(String projectName, List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return new HashSet<>();
        }
        
        String cypher = """
            MATCH (p:CodePackage)
            WHERE p.projectName = $projectName
              AND (p.id IN $qualifiedNames OR p.qualifiedName IN $qualifiedNames)
            RETURN DISTINCT COALESCE(p.id, p.qualifiedName) AS id
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> record.get("id").asString())
                .collect(Collectors.toSet());
        }
    }

    @Override
    public void insertPackagesBatch(List<CodePackageDO> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        
        log.debug("批量插入包开始: count={}", packages.size());
        
        String insertCypher = """
            UNWIND $packages AS pkg
            MERGE (p:CodePackage {id: pkg.id})
            SET p.name = pkg.name,
                p.qualifiedName = pkg.qualifiedName,
                p.language = pkg.language,
                p.projectName = pkg.projectName,
                p.projectFilePath = pkg.projectFilePath,
                p.gitRepoUrl = pkg.gitRepoUrl,
                p.gitBranch = pkg.gitBranch,
                p.packagePath = pkg.packagePath
            """;
        
        List<Map<String, Object>> params = packages.stream()
            .map(this::packageToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(insertCypher, Values.parameters("packages", params));
            log.info("批量插入包成功: count={}", packages.size());
        } catch (Exception e) {
            log.error("批量插入包失败: count={}, error={}", packages.size(), e.getMessage(), e);
            throw new RuntimeException("批量插入包失败", e);
        }
    }

    @Override
    public void updatePackagesBatch(List<CodePackageDO> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        
        log.debug("批量更新包开始: count={}", packages.size());
        
        String updateCypher = """
            UNWIND $packages AS pkg
            MATCH (p:CodePackage {id: pkg.id, projectName: pkg.projectName})
            SET p.name = pkg.name,
                p.qualifiedName = pkg.qualifiedName,
                p.language = pkg.language,
                p.projectName = pkg.projectName,
                p.projectFilePath = pkg.projectFilePath,
                p.gitRepoUrl = pkg.gitRepoUrl,
                p.gitBranch = pkg.gitBranch,
                p.packagePath = pkg.packagePath
            """;
        
        List<Map<String, Object>> params = packages.stream()
            .map(this::packageToMap)
            .collect(Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(updateCypher, Values.parameters("packages", params));
            log.info("批量更新包成功: count={}", packages.size());
        } catch (Exception e) {
            log.error("批量更新包失败: count={}, error={}", packages.size(), e.getMessage(), e);
            throw new RuntimeException("批量更新包失败", e);
        }
    }

    private Map<String, Object> packageToMap(CodePackageDO pkg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", pkg.getId());
        map.put("name", pkg.getName());
        map.put("qualifiedName", pkg.getQualifiedName());
        map.put("language", pkg.getLanguage());
        map.put("projectName", pkg.getProjectName());
        map.put("projectFilePath", pkg.getProjectFilePath());
        map.put("gitRepoUrl", pkg.getGitRepoUrl());
        map.put("gitBranch", pkg.getGitBranch());
        map.put("packagePath", pkg.getPackagePath());
        return map;
    }
}
