package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 端点仓储实现
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
public class Neo4jCodeEndpointRepository implements CodeEndpointRepository {
    
    private final Driver driver;
    
    public Neo4jCodeEndpointRepository(Driver driver) {
        this.driver = driver;
    }
    
    @Override
    public void insertEndpointsBatch(List<CodeEndpointDO> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }
        
        String cypher = """
            UNWIND $endpoints AS endpoint
            MERGE (e:CodeEndpoint {id: endpoint.id})
            SET e.name = endpoint.name,
                e.qualifiedName = endpoint.qualifiedName,
                e.projectFilePath = endpoint.projectFilePath,
                e.gitRepoUrl = endpoint.gitRepoUrl,
                e.gitBranch = endpoint.gitBranch,
                e.language = endpoint.language,
                e.projectName = endpoint.projectName,
                e.startLine = endpoint.startLine,
                e.endLine = endpoint.endLine,
                e.endpointType = endpoint.endpointType,
                e.direction = endpoint.direction,
                e.isExternal = endpoint.isExternal,
                e.httpMethod = endpoint.httpMethod,
                e.path = endpoint.path,
                e.normalizedPath = endpoint.normalizedPath,
                e.topic = endpoint.topic,
                e.operation = endpoint.operation,
                e.brokerType = endpoint.brokerType,
                e.keyPattern = endpoint.keyPattern,
                e.command = endpoint.command,
                e.dataStructure = endpoint.dataStructure,
                e.tableName = endpoint.tableName,
                e.dbOperation = endpoint.dbOperation,
                e.serviceName = endpoint.serviceName,
                e.parseLevel = endpoint.parseLevel,
                e.targetService = endpoint.targetService,
                e.matchIdentity = endpoint.matchIdentity
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("endpoints", toMapList(endpoints)));
            log.info("批量插入端点成功: count={}", endpoints.size());
        } catch (Exception e) {
            log.error("批量插入端点失败: error={}", e.getMessage(), e);
            throw new RuntimeException("批量插入端点失败", e);
        }
    }
    
    @Override
    public void updateEndpointsBatch(List<CodeEndpointDO> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }
        
        String cypher = """
            UNWIND $endpoints AS endpoint
            MATCH (e:CodeEndpoint {id: endpoint.id, projectName: endpoint.projectName})
            SET e.name = endpoint.name,
                e.qualifiedName = endpoint.qualifiedName,
                e.projectFilePath = endpoint.projectFilePath,
                e.gitRepoUrl = endpoint.gitRepoUrl,
                e.gitBranch = endpoint.gitBranch,
                e.language = endpoint.language,
                e.projectName = endpoint.projectName,
                e.startLine = endpoint.startLine,
                e.endLine = endpoint.endLine,
                e.endpointType = endpoint.endpointType,
                e.direction = endpoint.direction,
                e.isExternal = endpoint.isExternal,
                e.httpMethod = endpoint.httpMethod,
                e.path = endpoint.path,
                e.normalizedPath = endpoint.normalizedPath,
                e.topic = endpoint.topic,
                e.operation = endpoint.operation,
                e.brokerType = endpoint.brokerType,
                e.keyPattern = endpoint.keyPattern,
                e.command = endpoint.command,
                e.dataStructure = endpoint.dataStructure,
                e.tableName = endpoint.tableName,
                e.dbOperation = endpoint.dbOperation,
                e.serviceName = endpoint.serviceName,
                e.parseLevel = endpoint.parseLevel,
                e.targetService = endpoint.targetService,
                e.matchIdentity = endpoint.matchIdentity
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("endpoints", toMapList(endpoints)));
            log.info("批量更新端点成功: count={}", endpoints.size());
        } catch (Exception e) {
            log.error("批量更新端点失败: error={}", e.getMessage(), e);
            throw new RuntimeException("批量更新端点失败", e);
        }
    }
    
    @Override
    public void deleteById(String projectName, String id) {
        String cypher = """
            MATCH (e:CodeEndpoint {projectName: $projectName, id: $id})
            DETACH DELETE e
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("projectName", projectName, "id", id));
            log.debug("删除端点成功: id={}", id);
        } catch (Exception e) {
            log.error("删除端点失败: id={}, error={}", id, e.getMessage(), e);
            throw new RuntimeException("删除端点失败: " + id, e);
        }
    }
    
    @Override
    public Set<String> findExistingEndpointsByIds(String projectName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        
        String cypher = """
            MATCH (e:CodeEndpoint)
            WHERE e.projectName = $projectName
              AND e.id IN $ids
            RETURN e.id AS id
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("projectName", projectName, "ids", ids));
            return result.stream()
                .map(record -> record.get("id").asString())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("查询端点存在性失败: error={}", e.getMessage(), e);
            throw new RuntimeException("查询端点存在性失败", e);
        }
    }
    
    @Override
    public List<CodeEndpointDO> findEndpointsByProjectFilePath(String projectName, String projectFilePath) {
        String cypher = """
            MATCH (e:CodeEndpoint {projectName: $projectName, projectFilePath: $projectFilePath})
            RETURN e
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("projectName", projectName, "projectFilePath", projectFilePath));
            return parseEndpointsFromResult(result);
        } catch (Exception e) {
            log.error("查询端点失败: projectFilePath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询端点失败: " + projectFilePath, e);
        }
    }

    @Override
    public List<CodeEndpointDO> findEndpointsByDirection(String projectName, String direction) {
        String cypher = """
            MATCH (e:CodeEndpoint {projectName: $projectName, direction: $direction})
            RETURN e
            """;

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("projectName", projectName, "direction", direction));
            return parseEndpointsFromResult(result);
        } catch (Exception e) {
            log.error("查询项目端点失败: projectName={}, direction={}, error={}", projectName, direction, e.getMessage(), e);
            throw new RuntimeException("查询项目端点失败: " + projectName + "/" + direction, e);
        }
    }
    
    @Override
    public List<CodeEndpointDO> findEndpointsByMatchIdentity(String matchIdentity, String direction) {
        String cypher;
        Map<String, Object> params = new HashMap<>();
        params.put("matchIdentity", matchIdentity);
        
        if (direction != null && !direction.isEmpty()) {
            cypher = """
                MATCH (e:CodeEndpoint {matchIdentity: $matchIdentity, direction: $direction})
                RETURN e
                """;
            params.put("direction", direction);
        } else {
            cypher = """
                MATCH (e:CodeEndpoint {matchIdentity: $matchIdentity})
                RETURN e
                """;
        }
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, params);
            return parseEndpointsFromResult(result);
        } catch (Exception e) {
            log.error("查询端点失败: matchIdentity={}, direction={}, error={}", 
                matchIdentity, direction, e.getMessage(), e);
            throw new RuntimeException("查询端点失败: " + matchIdentity, e);
        }
    }
    
    /**
     * 从 Neo4j 结果中解析端点列表
     */
    private List<CodeEndpointDO> parseEndpointsFromResult(Result result) {
        List<CodeEndpointDO> endpoints = new ArrayList<>();
        
        result.stream().forEach(record -> {
            var node = record.get("e").asNode();
            CodeEndpointDO endpoint = new CodeEndpointDO();
            endpoint.setId(node.get("id").asString());
            endpoint.setName(node.get("name").asString(null));
            endpoint.setQualifiedName(node.get("qualifiedName").asString(null));
            endpoint.setProjectFilePath(node.get("projectFilePath").asString(null));
            endpoint.setGitRepoUrl(node.get("gitRepoUrl").asString(null));
            endpoint.setGitBranch(node.get("gitBranch").asString(null));
            endpoint.setLanguage(node.get("language").asString(null));
            endpoint.setProjectName(node.get("projectName").asString(null));
            endpoint.setStartLine(node.get("startLine").asInt(0));
            endpoint.setEndLine(node.get("endLine").asInt(0));
            endpoint.setEndpointType(node.get("endpointType").asString(null));
            endpoint.setDirection(node.get("direction").asString(null));
            endpoint.setIsExternal(node.get("isExternal").asBoolean(false));
            endpoint.setHttpMethod(node.get("httpMethod").asString(null));
            endpoint.setPath(node.get("path").asString(null));
            endpoint.setNormalizedPath(node.get("normalizedPath").asString(null));
            endpoint.setTopic(node.get("topic").asString(null));
            endpoint.setOperation(node.get("operation").asString(null));
            endpoint.setBrokerType(node.get("brokerType").asString(null));
            endpoint.setKeyPattern(node.get("keyPattern").asString(null));
            endpoint.setCommand(node.get("command").asString(null));
            endpoint.setDataStructure(node.get("dataStructure").asString(null));
            endpoint.setTableName(node.get("tableName").asString(null));
            endpoint.setDbOperation(node.get("dbOperation").asString(null));
            endpoint.setServiceName(node.get("serviceName").asString(null));
            endpoint.setParseLevel(node.get("parseLevel").asString(null));
            endpoint.setTargetService(node.get("targetService").asString(null));
            endpoint.setMatchIdentity(node.get("matchIdentity").asString(null));
            // functionId 不再持久化
            endpoints.add(endpoint);
        });
        
        return endpoints;
    }
    
    private List<Map<String, Object>> toMapList(List<CodeEndpointDO> endpoints) {
        return endpoints.stream().map(this::toMap).collect(Collectors.toList());
    }
    
    private Map<String, Object> toMap(CodeEndpointDO endpoint) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", endpoint.getId());
        map.put("name", endpoint.getName());
        map.put("qualifiedName", endpoint.getQualifiedName());
        map.put("projectFilePath", endpoint.getProjectFilePath());
        map.put("gitRepoUrl", endpoint.getGitRepoUrl());
        map.put("gitBranch", endpoint.getGitBranch());
        map.put("language", endpoint.getLanguage());
        map.put("projectName", endpoint.getProjectName());
        map.put("startLine", endpoint.getStartLine());
        map.put("endLine", endpoint.getEndLine());
        map.put("endpointType", endpoint.getEndpointType());
        map.put("direction", endpoint.getDirection());
        map.put("isExternal", endpoint.getIsExternal());
        map.put("httpMethod", endpoint.getHttpMethod());
        map.put("path", endpoint.getPath());
        map.put("normalizedPath", endpoint.getNormalizedPath());
        map.put("topic", endpoint.getTopic());
        map.put("operation", endpoint.getOperation());
        map.put("brokerType", endpoint.getBrokerType());
        map.put("keyPattern", endpoint.getKeyPattern());
        map.put("command", endpoint.getCommand());
        map.put("dataStructure", endpoint.getDataStructure());
        map.put("tableName", endpoint.getTableName());
        map.put("dbOperation", endpoint.getDbOperation());
        map.put("serviceName", endpoint.getServiceName());
        map.put("parseLevel", endpoint.getParseLevel());
        map.put("targetService", endpoint.getTargetService());
        map.put("matchIdentity", endpoint.getMatchIdentity());
        // functionId 不再持久化
        return map;
    }
}
