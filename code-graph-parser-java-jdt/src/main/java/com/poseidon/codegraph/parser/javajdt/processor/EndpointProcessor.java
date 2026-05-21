package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.*;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import com.poseidon.codegraph.parser.javajdt.endpoint.EndpointParsingService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * 端点处理器
 * 
 * 职责：
 * - 使用 SER 规则解析端点（HTTP API、Kafka、Redis、DB 等）
 * - 关联端点与函数（设置 endpoint.function 字段）
 * - 构建端点关系：ENDPOINT_TO_FUNCTION、FUNCTION_TO_ENDPOINT
 * 
 * 优先级：100（在节点提取阶段完成后执行，依赖于 PackageProcessor、UnitProcessor、FunctionProcessor）
 */
@Slf4j
public class EndpointProcessor implements ASTNodeProcessor {
    
    private final EndpointParsingService endpointParsingService;
    
    public EndpointProcessor() {
        this.endpointParsingService = new EndpointParsingService();
        this.endpointParsingService.init();  // 初始化并加载 SER 规则
    }
    
    public EndpointProcessor(EndpointParsingService service) {
        this.endpointParsingService = service;
    }
    
    @Override
    public void onMethodDeclaration(
        MethodDeclaration method,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        // 检查是否有端点注解（inbound）
        // 注意：实际的端点提取由 SER 引擎完成，这里只是一个占位
        // 真正的逻辑在 EndpointParsingService 中
    }
    
    @Override
    public void onMethodInvocation(
        MethodInvocation invocation,
        MethodDeclaration enclosingMethod,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        // 检查是否是 REST 调用（outbound）
        // 注意：实际的端点提取由 SER 引擎完成，这里只是一个占位
        // 真正的逻辑在 EndpointParsingService 中
    }
    
    @Override
    public void onTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        if (!(type instanceof TypeDeclaration typeDeclaration)) {
            return;
        }
        // 在处理完整个类之后，调用 SER 引擎解析端点
        // 这样可以一次性获取该类中的所有端点
        try {
            List<CodeEndpoint> endpoints = endpointParsingService.parseEndpointsForType(
                typeDeclaration,
                context.getCompilationUnit(),
                context.getPackageName() != null ? context.getPackageName() : "",
                extractFileName(context.getProjectFilePath()),
                context.getProjectFilePath(),
                context.getAbsoluteFilePath(),
                context.getExternalValues()
            );
            
            if (endpoints.isEmpty()) {
                return;
            }
            
            log.debug("类 {} 中发现 {} 个端点", type.getName().getIdentifier(), endpoints.size());
            
            // 只提取端点节点，不建立关系（关系在 onTraversalComplete 中建立）
            for (CodeEndpoint endpoint : endpoints) {
                // 设置 Git 信息
                endpoint.setGitRepoUrl(context.getGitRepoUrl());
                endpoint.setGitBranch(context.getGitBranch());
                
                // 添加端点到 graph（暂时不建立关系）
                context.getGraph().addEndpoint(endpoint);
                
                log.debug("提取端点: {} {}", endpoint.getEndpointType(), endpoint.getName());
            }
            
        } catch (Exception e) {
            log.error("端点解析失败: class={}, error={}", 
                type.getName().getIdentifier(), e.getMessage(), e);
        }
    }
    
    @Override
    public void onTraversalComplete(ProcessorContext context) {
        // 在 AST 遍历完成后，所有节点都已提取，现在建立端点与函数的关系
        CodeGraph graph = context.getGraph();
        List<CodeEndpoint> allEndpoints = graph.getEndpointsAsList();
        
        if (allEndpoints.isEmpty()) {
            return;
        }

        // 1. 先进行端点去重（防止同一端点产生多条关系）
        java.util.Map<String, CodeEndpoint> uniqueEndpoints = new java.util.LinkedHashMap<>();
        for (CodeEndpoint endpoint : allEndpoints) {
            uniqueEndpoints.putIfAbsent(endpoint.getId(), endpoint);
        }
        
        List<CodeEndpoint> deduplicatedEndpoints = new ArrayList<>(uniqueEndpoints.values());
        
        log.debug("开始构建端点关系，原始 {} 个，去重后 {} 个", 
            allEndpoints.size(), deduplicatedEndpoints.size());
        
        int relationshipCount = 0;
        for (CodeEndpoint endpoint : deduplicatedEndpoints) {
            // 从 graph 中查找对应的 CodeFunction
            CodeFunction function = findFunctionByEndpoint(graph, endpoint);
            if (function != null) {
                // 设置关联（临时字段，不持久化）
                endpoint.setFunction(function);
                
                // 构建关系
                buildEndpointRelationship(endpoint, function, context);
                
                relationshipCount++;
                log.info("✓ 端点: {} → 函数: {}", 
                    endpoint.getName(), 
                    function.getName());
            } else {
                log.warn("✗ 端点未找到对应函数: {}", 
                    endpoint.getName());
            }
        }
        
        log.info("端点关系构建完成: {} / {} 个端点成功关联函数", 
            relationshipCount, deduplicatedEndpoints.size());
    }
    
    @Override
    public int getPriority() {
        return 100;  // 在节点提取阶段（1-4）之后，与关系构建阶段并行
    }
    
    @Override
    public String getName() {
        return "EndpointProcessor";
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 从 graph 中查找端点对应的 CodeFunction
     * 
     * 查找策略：
     * 1. 如果 endpoint 有 functionId（从 SER 提取），用它查找
     * 2. 否则根据 endpoint 的位置信息（startLine）查找
     */
    private CodeFunction findFunctionByEndpoint(CodeGraph graph, CodeEndpoint endpoint) {
        // 策略 1：通过 functionId 查找
        String functionId = endpoint.getFunctionId();
        log.debug("查找端点对应函数: endpoint={}, functionId={}, startLine={}", 
            endpoint.getName(), functionId, endpoint.getStartLine());
        
        if (functionId != null && !functionId.isEmpty()) {
            log.debug("尝试通过 functionId 查找: {}", functionId);
            for (CodeUnit unit : graph.getUnitsAsList()) {
                log.debug("检查 Unit: {}, functions.size={}", unit.getQualifiedName(), unit.getFunctions().size());
                for (CodeFunction function : unit.getFunctions()) {
                    log.debug("  - Function: id={}, qualifiedName={}", function.getId(), function.getQualifiedName());
                    if (functionId.equals(function.getId()) || 
                        functionId.equals(function.getQualifiedName())) {
                        log.debug("✓ 通过 functionId 找到函数: {}", function.getQualifiedName());
                        return function;
                    }
                }
            }
            log.debug("✗ 通过 functionId 未找到函数");
        }
        
        // 策略 2：通过位置信息查找（fallback）
        if (endpoint.getStartLine() != null) {
            log.debug("尝试通过位置信息查找: startLine={}", endpoint.getStartLine());
            for (CodeUnit unit : graph.getUnitsAsList()) {
                for (CodeFunction function : unit.getFunctions()) {
                    if (function.getStartLine() != null && function.getEndLine() != null &&
                        endpoint.getStartLine() >= function.getStartLine() &&
                        endpoint.getStartLine() <= function.getEndLine()) {
                        log.debug("✓ 通过位置信息找到函数: {}", function.getQualifiedName());
                        return function;
                    }
                }
            }
            log.debug("✗ 通过位置信息未找到函数");
        }
        
        log.debug("✗ 所有策略均未找到函数");
        return null;
    }
    
    /**
     * 构建端点与函数的关系
     */
    private void buildEndpointRelationship(
        CodeEndpoint endpoint,
        CodeFunction function,
        ProcessorContext context
    ) {
        CodeRelationship rel = new CodeRelationship();
        
        // 根据端点方向设置关系类型
        if ("inbound".equals(endpoint.getDirection())) {
            // inbound: Endpoint -> Function
            rel.setRelationshipType(RelationshipType.ENDPOINT_TO_FUNCTION);
            rel.setFromNodeId(endpoint.getId());
            rel.setToNodeId(function.getId());
        } else if ("outbound".equals(endpoint.getDirection())) {
            // outbound: Function -> Endpoint
            rel.setRelationshipType(RelationshipType.FUNCTION_TO_ENDPOINT);
            rel.setFromNodeId(function.getId());
            rel.setToNodeId(endpoint.getId());
        }
        rel.setId(JdtGraphIds.relationshipId(rel.getFromNodeId(), rel.getRelationshipType(), rel.getToNodeId()));
        
        rel.setLanguage("java");
        context.getGraph().addRelationship(rel);
        
        log.debug("构建端点关系: {} -[{}]-> {}", 
            rel.getFromNodeId(), rel.getRelationshipType(), rel.getToNodeId());
    }
    
    private String extractFileName(String projectFilePath) {
        if (projectFilePath == null) {
            return "";
        }
        int lastSlash = Math.max(projectFilePath.lastIndexOf('/'), projectFilePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? projectFilePath.substring(lastSlash + 1) : projectFilePath;
    }
}
