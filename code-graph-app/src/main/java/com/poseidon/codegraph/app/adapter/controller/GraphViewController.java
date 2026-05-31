package com.poseidon.codegraph.app.adapter.controller;

import com.poseidon.codegraph.app.adapter.dto.ApiResponse;
import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;
import com.poseidon.codegraph.engine.application.model.CodePackageDO;
import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.CodeUnitDO;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Read APIs used by the bundled graph workbench.
 */
@RestController
@RequestMapping("/api/graph")
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "memory", matchIfMissing = true)
public class GraphViewController {

    private final InMemoryCodeGraphRepository repository;

    public GraphViewController(InMemoryCodeGraphRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/metadata")
    public ApiResponse<GraphMetadata> metadata() {
        List<GraphNode> nodes = allNodes();
        List<CodeRelationshipDO> relationships = repository.findAllRelationships();
        return ApiResponse.success(new GraphMetadata(
            nodes.stream().map(GraphNode::gitRepoUrl).filter(this::hasText).distinct().sorted().toList(),
            nodes.stream().map(GraphNode::type).filter(this::hasText).distinct().sorted().toList(),
            relationships.stream().map(CodeRelationshipDO::getRelationshipType).filter(this::hasText).distinct().sorted().toList()
        ));
    }

    @GetMapping("/overview")
    public ApiResponse<GraphResponse> overview(
            @RequestParam(required = false) String gitRepoUrl,
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(required = false) List<String> relationshipTypes,
            @RequestParam(defaultValue = "320") int limit,
            @RequestParam(defaultValue = "1000") int relLimit) {
        return ApiResponse.success(graph(gitRepoUrl, nodeTypes, relationshipTypes, limit, relLimit));
    }

    @GetMapping("/search")
    public ApiResponse<GraphResponse> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String gitRepoUrl,
            @RequestParam(required = false) List<String> nodeTypes,
            @RequestParam(defaultValue = "80") int limit) {
        String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<GraphNode> nodes = filterNodes(gitRepoUrl, nodeTypes).stream()
            .filter(node -> containsIgnoreCase(node.name(), normalized)
                || containsIgnoreCase(node.qualifiedName(), normalized)
                || containsIgnoreCase(node.path(), normalized))
            .limit(limit)
            .toList();
        return ApiResponse.success(new GraphResponse(nodes, List.of()));
    }

    @PostMapping("/traverse")
    public ApiResponse<GraphResponse> traverse(@RequestBody TraverseRequest request) {
        if (request == null || !hasText(request.startNodeId())) {
            return ApiResponse.success(new GraphResponse(List.of(), List.of()));
        }
        int maxDepth = request.maxDepth() == null ? 2 : Math.max(1, request.maxDepth());
        Set<String> visited = new LinkedHashSet<>();
        Set<String> frontier = new LinkedHashSet<>();
        frontier.add(request.startNodeId());
        visited.add(request.startNodeId());

        for (int depth = 0; depth < maxDepth; depth++) {
            Set<String> next = new LinkedHashSet<>();
            for (CodeRelationshipDO relationship : repository.findAllRelationships()) {
                if (frontier.contains(relationship.getFromNodeId()) && visited.add(relationship.getToNodeId())) {
                    next.add(relationship.getToNodeId());
                }
                if (frontier.contains(relationship.getToNodeId()) && visited.add(relationship.getFromNodeId())) {
                    next.add(relationship.getFromNodeId());
                }
            }
            frontier = next;
            if (frontier.isEmpty()) {
                break;
            }
        }

        List<GraphNode> nodes = allNodes().stream()
            .filter(node -> visited.contains(node.id()))
            .toList();
        List<GraphRelationship> relationships = relationshipsForNodeIds(visited, null, 1000);
        return ApiResponse.success(new GraphResponse(nodes, relationships));
    }

    @PostMapping("/trace")
    public ApiResponse<GraphResponse> trace(@RequestBody TraverseRequest request) {
        return traverse(request);
    }

    @PostMapping("/query")
    public ApiResponse<GraphResponse> query(@RequestBody Map<String, Object> ignored) {
        return ApiResponse.success(graph(null, null, null, 320, 1000));
    }

    @GetMapping("/debug/node")
    public ApiResponse<GraphResponse> nodeDebug(@RequestParam String nodeName) {
        List<GraphNode> matched = allNodes().stream()
            .filter(node -> node.name().equals(nodeName) || node.qualifiedName().equals(nodeName) || node.id().equals(nodeName))
            .toList();
        Set<String> ids = matched.stream().map(GraphNode::id).collect(LinkedHashSet::new, Set::add, Set::addAll);
        return ApiResponse.success(new GraphResponse(matched, relationshipsForNodeIds(ids, null, 1000)));
    }

    private GraphResponse graph(String gitRepoUrl, List<String> nodeTypes, List<String> relationshipTypes, int limit, int relLimit) {
        List<GraphNode> nodes = filterNodes(gitRepoUrl, nodeTypes).stream()
            .limit(limit)
            .toList();
        Set<String> nodeIds = nodes.stream().map(GraphNode::id).collect(LinkedHashSet::new, Set::add, Set::addAll);
        return new GraphResponse(nodes, relationshipsForNodeIds(nodeIds, relationshipTypes, relLimit));
    }

    private List<GraphNode> filterNodes(String gitRepoUrl, List<String> nodeTypes) {
        return allNodes().stream()
            .filter(node -> !hasText(gitRepoUrl) || gitRepoUrl.equals(node.gitRepoUrl()))
            .filter(node -> nodeTypes == null || nodeTypes.isEmpty() || nodeTypes.contains(node.type()))
            .toList();
    }

    private List<GraphNode> allNodes() {
        return Stream.of(
                repository.findAllPackages().stream().map(this::node),
                repository.findAllUnits().stream().map(this::node),
                repository.findAllFunctions().stream().map(this::node),
                repository.findAllEndpoints().stream().map(this::node))
            .flatMap(stream -> stream)
            .toList();
    }

    private List<GraphRelationship> relationshipsForNodeIds(Set<String> nodeIds, List<String> relationshipTypes, int limit) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllRelationships().stream()
            .filter(rel -> nodeIds.contains(rel.getFromNodeId()) && nodeIds.contains(rel.getToNodeId()))
            .filter(rel -> relationshipTypes == null || relationshipTypes.isEmpty() || relationshipTypes.contains(rel.getRelationshipType()))
            .limit(limit)
            .map(rel -> new GraphRelationship(
                rel.getFromNodeId(),
                rel.getToNodeId(),
                rel.getRelationshipType(),
                null,
                null,
                rel.getLineNumber()))
            .toList();
    }

    private GraphNode node(CodePackageDO value) {
        return new GraphNode(
            value.getId(),
            value.getId(),
            "PACKAGE",
            value.getName(),
            value.getQualifiedName(),
            null,
            value.getGitRepoUrl(),
            null,
            null,
            null);
    }

    private GraphNode node(CodeUnitDO value) {
        return new GraphNode(
            value.getId(),
            value.getId(),
            "UNIT",
            value.getName(),
            value.getQualifiedName(),
            value.getProjectFilePath(),
            value.getGitRepoUrl(),
            null,
            null,
            null);
    }

    private GraphNode node(CodeFunctionDO value) {
        return new GraphNode(
            value.getId(),
            value.getId(),
            "FUNCTION",
            value.getName(),
            value.getQualifiedName(),
            value.getProjectFilePath(),
            value.getGitRepoUrl(),
            null,
            null,
            null);
    }

    private GraphNode node(CodeEndpointDO value) {
        return new GraphNode(
            value.getId(),
            value.getId(),
            value.getEndpointType() != null ? value.getEndpointType() : "ENDPOINT",
            value.getName(),
            value.getQualifiedName(),
            value.getProjectFilePath(),
            value.getGitRepoUrl(),
            firstText(value.getNormalizedPath(), value.getPath(), value.getRoutePath(), value.getTopic(), value.getTableName()),
            value.getHttpMethod(),
            null);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public record GraphMetadata(List<String> gitRepoUrls, List<String> nodeTypes, List<String> relationshipTypes) {
    }

    public record GraphResponse(List<GraphNode> nodes, List<GraphRelationship> relationships) {
    }

    public record GraphNode(
        String id,
        String elementId,
        String type,
        String name,
        String qualifiedName,
        String projectFilePath,
        String gitRepoUrl,
        String path,
        String httpMethod,
        Integer depth) {
    }

    public record GraphRelationship(
        String fromNodeId,
        String toNodeId,
        String relationshipType,
        String toNodeName,
        String toQualifiedName,
        Integer lineNumber) {
    }

    public record TraverseRequest(String startNodeId, Integer maxDepth, String direction) {
    }
}
