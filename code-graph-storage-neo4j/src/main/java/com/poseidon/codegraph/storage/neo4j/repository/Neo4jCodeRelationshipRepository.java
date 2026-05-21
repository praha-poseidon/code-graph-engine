package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.FileMetaInfo;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Repository
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
public class Neo4jCodeRelationshipRepository implements CodeRelationshipRepository {

    private final Driver neo4jDriver;

    public Neo4jCodeRelationshipRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public List<String> findWhoCallsMe(String projectName, String targetProjectFilePath) {
        log.debug("查询依赖文件: targetFile={}", targetProjectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE callee.projectName = $projectName
              AND caller.projectName = $projectName
              AND callee.projectFilePath = $targetProjectFilePath
            RETURN DISTINCT caller.projectFilePath AS projectFilePath
            """;
        
        try (Session session = neo4jDriver.session()) {
            List<String> result = session.run(cypher, Values.parameters("projectName", projectName, "targetProjectFilePath", targetProjectFilePath))
                .stream()
                .map(record -> record.get("projectFilePath").asString())
                .distinct()
                .collect(Collectors.toList());
            log.debug("查询依赖文件完成: targetFile={}, dependentCount={}", targetProjectFilePath, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询依赖文件失败: targetFile={}, error={}", targetProjectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询依赖文件失败: " + targetProjectFilePath, e);
        }
    }

    @Override
    public List<FileMetaInfo> findWhoCallsMeWithMeta(String projectName, String targetProjectFilePath) {
        log.debug("查询依赖文件（带 Git 元信息）: targetFile={}", targetProjectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE callee.projectName = $projectName
              AND caller.projectName = $projectName
              AND callee.projectFilePath = $targetProjectFilePath
            RETURN DISTINCT 
                caller.projectFilePath AS projectFilePath,
                caller.gitRepoUrl AS gitRepoUrl,
                caller.gitBranch AS gitBranch
            """;
        
        try (Session session = neo4jDriver.session()) {
            List<FileMetaInfo> result = session.run(cypher, Values.parameters("projectName", projectName, "targetProjectFilePath", targetProjectFilePath))
                .stream()
                .map(this::recordToFileMetaInfo)
                .distinct()
                .collect(Collectors.toList());
            log.debug("查询依赖文件完成（带 Git 元信息）: targetFile={}, dependentCount={}", targetProjectFilePath, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询依赖文件失败（带 Git 元信息）: targetFile={}, error={}", targetProjectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询依赖文件失败（带 Git 元信息）: " + targetProjectFilePath, e);
        }
    }

    private FileMetaInfo recordToFileMetaInfo(Record record) {
        FileMetaInfo meta = new FileMetaInfo();
        meta.setProjectFilePath(record.get("projectFilePath").asString(null));
        meta.setGitRepoUrl(record.get("gitRepoUrl").asString(null));
        meta.setGitBranch(record.get("gitBranch").asString(null));
        return meta;
    }

    @Override
    public void deleteFileOutgoingCalls(String projectName, String projectFilePath) {
        log.debug("删除文件出边: file={}", projectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[r:CALLS]->()
            WHERE caller.projectName = $projectName
              AND caller.projectFilePath = $projectFilePath
            DELETE r
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("projectName", projectName, "projectFilePath", projectFilePath));
            log.info("删除文件出边成功: file={}", projectFilePath);
        } catch (Exception e) {
            log.error("删除文件出边失败: file={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("删除文件出边失败: " + projectFilePath, e);
        }
    }

    @Override
    public void deleteById(String projectName, String relationshipId) {
        log.debug("删除关系: id={}", relationshipId);
        String cypher = """
            MATCH ()-[r]->()
            WHERE r.projectName = $projectName
              AND r.id = $relationshipId
            DELETE r
            """;

        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("projectName", projectName, "relationshipId", relationshipId));
            log.info("删除关系成功: id={}", relationshipId);
        } catch (Exception e) {
            log.error("删除关系失败: id={}, error={}", relationshipId, e.getMessage(), e);
            throw new RuntimeException("删除关系失败: " + relationshipId, e);
        }
    }

    @Override
    public void insertRelationshipsBatch(List<CodeRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return;
        }
        
        log.debug("批量插入关系开始: count={}", relationships.size());
        
        // 按关系类型分组（使用 Stream API）
        Map<String, List<CodeRelationshipDO>> groupedByType = relationships.stream()
            .collect(Collectors.groupingBy(CodeRelationshipDO::getRelationshipType));
        
        // 对每种类型的关系，使用枚举中的标签信息构造 Cypher 并执行
        groupedByType.forEach((typeName, rels) -> {
            try {
                // 从枚举获取标签信息
                com.poseidon.codegraph.model.RelationshipType relType = 
                    com.poseidon.codegraph.model.RelationshipType.valueOf(typeName);
                
                String fromLabel = relType.getFromLabel();
                String toLabel = relType.getToLabel();
                
                // 构造通用 Cypher（标签从枚举获取，不再硬编码）
                String cypher = buildInsertCypher(fromLabel, toLabel, typeName);
                
                // 转换为参数
                List<Map<String, Object>> params = rels.stream()
                    .map(this::relationshipToMap)
                    .collect(Collectors.toList());
                
                // 执行插入
                try (Session session = neo4jDriver.session()) {
                    session.run(cypher, Values.parameters("relationships", params));
                    log.info("批量插入 {} 关系成功: count={}", typeName, rels.size());
                } catch (Exception e) {
                    log.error("批量插入 {} 关系失败: count={}, error={}", typeName, rels.size(), e.getMessage(), e);
                    throw new RuntimeException("批量插入 " + typeName + " 关系失败", e);
                }
                
            } catch (IllegalArgumentException e) {
                log.error("未知的关系类型: {}", typeName, e);
                throw new RuntimeException("未知的关系类型: " + typeName, e);
            }
        });
        
        log.info("批量插入关系完成: total={}, 类型分布={}", 
                 relationships.size(), 
                 groupedByType.entrySet().stream()
                     .map(e -> e.getKey() + "=" + e.getValue().size())
                     .collect(Collectors.joining(", ")));
    }
    
    /**
     * 构造插入关系的 Cypher 语句
     * 
     * @param fromLabel 源节点标签（从枚举获取）
     * @param toLabel 目标节点标签（从枚举获取）
     * @param relationshipType 关系类型名称
     * @return Cypher 语句
     */
    private String buildInsertCypher(String fromLabel, String toLabel, String relationshipType) {
        // 使用 String.format 动态构造 Cypher（标签不再硬编码）
        return String.format("""
            UNWIND $relationships AS rel
            MATCH (from:%s {id: rel.fromNodeId})
            MATCH (to:%s {id: rel.toNodeId})
            MERGE (from)-[r:%s {id: rel.id}]->(to)
            SET r.id = rel.id,
                r.fromNodeId = rel.fromNodeId,
                r.toNodeId = rel.toNodeId,
                r.relationshipType = rel.relationshipType,
                r.lineNumber = rel.lineNumber,
                r.callType = rel.callType,
                r.language = rel.language,
                r.projectName = rel.projectName
            """, fromLabel, toLabel, relationshipType);
    }

    /**
     * 将关系 DO 转换为 Map（用于 Cypher 参数）
     */
    private Map<String, Object> relationshipToMap(CodeRelationshipDO relationship) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", relationship.getId());
        map.put("fromNodeId", relationship.getFromNodeId());
        map.put("toNodeId", relationship.getToNodeId());
        map.put("relationshipType", relationship.getRelationshipType());
        map.put("lineNumber", relationship.getLineNumber());
        map.put("callType", relationship.getCallType());
        map.put("language", relationship.getLanguage());
        map.put("projectName", relationship.getProjectName());
        return map;
    }

    @Override
    public List<CodeRelationshipDO> findOutgoingRelationships(String projectName, String nodeId, String relationshipType) {
        String cypher = """
            MATCH (from {projectName: $projectName, id: $nodeId})-[r]->()
            WHERE r.projectName = $projectName
              AND ($relationshipType IS NULL OR type(r) = $relationshipType)
            RETURN r
            """;
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters(
                    "projectName", projectName,
                    "nodeId", nodeId,
                    "relationshipType", relationshipType))
                .stream()
                .map(record -> mapToRelationshipDO(record.get("r").asMap()))
                .collect(Collectors.toList());
        }
    }

    @Override
    public List<CodeRelationshipDO> findIncomingRelationships(String projectName, String nodeId, String relationshipType) {
        String cypher = """
            MATCH ()-[r]->(to {projectName: $projectName, id: $nodeId})
            WHERE r.projectName = $projectName
              AND ($relationshipType IS NULL OR type(r) = $relationshipType)
            RETURN r
            """;
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters(
                    "projectName", projectName,
                    "nodeId", nodeId,
                    "relationshipType", relationshipType))
                .stream()
                .map(record -> mapToRelationshipDO(record.get("r").asMap()))
                .collect(Collectors.toList());
        }
    }

    private CodeRelationshipDO mapToRelationshipDO(Map<String, Object> map) {
        CodeRelationshipDO relationship = new CodeRelationshipDO();
        relationship.setId((String) map.get("id"));
        relationship.setFromNodeId((String) map.get("fromNodeId"));
        relationship.setToNodeId((String) map.get("toNodeId"));
        relationship.setRelationshipType((String) map.get("relationshipType"));
        relationship.setLineNumber(map.get("lineNumber") instanceof Number number ? number.intValue() : null);
        relationship.setCallType((String) map.get("callType"));
        relationship.setLanguage((String) map.get("language"));
        relationship.setProjectName((String) map.get("projectName"));
        return relationship;
    }

    @Override
    public java.util.Set<String> findExistingStructureRelationships(String projectName, List<CodeRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 构建查询条件：检查 (fromNodeId, toNodeId, relationshipType) 的组合是否存在
        List<Map<String, Object>> relPairs = relationships.stream()
            .map(rel -> {
                Map<String, Object> pair = new HashMap<>();
                pair.put("fromNodeId", rel.getFromNodeId());
                pair.put("toNodeId", rel.getToNodeId());
                pair.put("relationshipType", rel.getRelationshipType());
                return pair;
            })
            .collect(Collectors.toList());
        
        String cypher = """
            UNWIND $relPairs AS pair
            MATCH (from)-[r]->(to)
            WHERE from.id = pair.fromNodeId 
              AND to.id = pair.toNodeId
              AND from.projectName = $projectName
              AND to.projectName = $projectName
              AND r.projectName = $projectName
              AND type(r) = pair.relationshipType
            RETURN pair.fromNodeId + ':' + pair.toNodeId + ':' + pair.relationshipType AS key
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("projectName", projectName, "relPairs", relPairs))
                .stream()
                .map(record -> record.get("key").asString())
                .collect(Collectors.toSet());
        }
    }
}
