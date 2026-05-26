package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.*;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.delta.ParseRequest;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.model.event.CodeChangeEvent;
import com.poseidon.codegraph.engine.domain.service.delta.GraphDeltaProjectScopeNormalizer;
import com.poseidon.codegraph.engine.domain.service.delta.GraphDeltaApplyService;
import com.poseidon.codegraph.spi.CodeGraphParser;
import com.poseidon.codegraph.spi.CodeGraphParserRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 抽象代码变更处理器
 * 提供通用辅助方法
 */
@Slf4j
public abstract class AbstractChangeProcessor implements CodeChangeProcessor {

    private final GraphDeltaApplyService graphDeltaApplyService = new GraphDeltaApplyService();
    private final GraphDeltaProjectScopeNormalizer projectScopeNormalizer = new GraphDeltaProjectScopeNormalizer();
    
    protected AbstractChangeProcessor() {
    }
    
    protected GraphDelta parseGraphDelta(CodeGraphContext context, String absoluteFilePath, String projectFilePath) {
        String language = context.getLanguage() == null || context.getLanguage().isBlank() ? "java" : context.getLanguage();
        CodeGraphParser parser = parserRegistry(context)
            .find(language)
            .orElseThrow(() -> new IllegalStateException("No code graph parser registered for language: " + language));
        ParseRequest request = new ParseRequest(
            context.getProjectName(),
            language,
            projectRoot(absoluteFilePath, projectFilePath),
            List.of(absoluteFilePath),
            toList(context.getSourcepathEntries()),
            toList(context.getClasspathEntries()),
            context.getGitRepoUrl(),
            context.getGitBranch(),
            context.getChangeType(),
            context.getEndpointRuleSources(),
            context.getTraceRuleSources(),
            context.getExternalValues(),
            Map.of("projectFilePath", projectFilePath)
        );
        return projectScopeNormalizer.normalize(parser.parse(request), context.getProjectName());
    }

    private CodeGraphParserRegistry parserRegistry(CodeGraphContext context) {
        if (context.getParserRegistry() != null) {
            return context.getParserRegistry();
        }
        return CodeGraphParserRegistry.loadFromServiceLoader();
    }

    private List<String> toList(String[] values) {
        return values == null ? List.of() : Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList();
    }

    private String projectRoot(String absoluteFilePath, String projectFilePath) {
        if (absoluteFilePath == null || projectFilePath == null || projectFilePath.isBlank()) {
            return null;
        }
        String normalizedAbsolute = absoluteFilePath.replace('\\', '/');
        String normalizedProject = projectFilePath.replace('\\', '/');
        if (normalizedAbsolute.endsWith(normalizedProject)) {
            return normalizedAbsolute.substring(0, normalizedAbsolute.length() - normalizedProject.length())
                .replaceAll("/$", "");
        }
        return null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
    
    protected void saveNodes(GraphDelta delta, CodeGraphContext context) {
        graphDeltaApplyService.apply(delta, context);
    }
    
    protected void deleteNodes(List<CodeUnit> units, List<CodeFunction> fileFunctions, 
                            List<com.poseidon.codegraph.model.CodeEndpoint> endpoints,
                            CodeGraphContext context) {
        units.forEach(unit -> context.getWriter().getDeleteNode().accept(unit.getId()));
        fileFunctions.forEach(func -> context.getWriter().getDeleteNode().accept(func.getId()));
        endpoints.forEach(endpoint -> context.getWriter().getDeleteNode().accept(endpoint.getId()));
    }
    
    protected int rebuildFileCallRelationships(CodeGraphContext context, String absoluteFilePath, String projectFilePath,
                                            GraphDelta delta) {
        log.debug("开始重建调用关系: file={}", projectFilePath);
        
        if (delta == null) {
            log.debug("delta 为空，重新解析文件: {}", projectFilePath);
            delta = parseGraphDelta(context, absoluteFilePath, projectFilePath);
        }
        
        java.util.List<CodeRelationship> relationships = safeList(delta.relationships());
        // 只处理 CALLS 关系
        java.util.List<CodeRelationship> callRelationships = relationships.stream()
            .filter(rel -> rel.getRelationshipType() == RelationshipType.CALLS)
            .collect(java.util.stream.Collectors.toList());
        
        if (callRelationships.isEmpty()) {
            log.info("文件没有调用关系: file={}", projectFilePath);
            return 0;
        }
        
        log.info("文件包含 {} 条调用关系: file={}", callRelationships.size(), projectFilePath);
        
        // 批量查询：收集所有需要检查的节点ID
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (CodeRelationship rel : callRelationships) {
            nodeIds.add(rel.getFromNodeId());
            nodeIds.add(rel.getToNodeId());
        }
        
        log.debug("收集到 {} 个需要检查的节点ID", nodeIds.size());
        
        // 批量查询哪些节点已存在
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingFunctionsByQualifiedNames()
            .apply(new java.util.ArrayList<>(nodeIds));
        
        log.debug("数据库中已存在 {} 个节点", existingIds.size());
        
        // 批量创建占位符节点（不存在的节点）
        java.util.List<CodeFunction> placeholders = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            if (!existingIds.contains(nodeId)) {
                // 从 relationships 中获取 language（假设同一个文件的关系使用相同语言）
                String language = relationships.get(0).getLanguage();
                CodeFunction placeholder = createPlaceholderFunction(nodeId, language);
                placeholders.add(placeholder);
                log.debug("需要创建占位符节点: {}", nodeId);
            }
        }
        
        // 批量插入占位符节点（占位符节点是新创建的，直接插入）
        if (!placeholders.isEmpty()) {
            log.info("批量创建占位符节点: count={}", placeholders.size());
            context.getWriter().getInsertFunctionsBatch().accept(placeholders);
        }
        
        // 批量插入调用关系（调用关系是新创建的，直接插入）
        log.info("批量插入调用关系: count={}", callRelationships.size());
        context.getWriter().getInsertRelationshipsBatch().accept(callRelationships);
        
        return callRelationships.size();
    }
    
    /**
     * 创建占位符函数节点
     * 业务规则：当调用关系的目标节点不存在时，创建占位符节点
     * 
     * @param qualifiedName 全限定名（格式：com.example.Class.method(params)）
     * @param language 语言
     * @return 占位符节点
     */
    private CodeFunction createPlaceholderFunction(String qualifiedName, String language) {
        CodeFunction placeholder = new CodeFunction();
        placeholder.setId(qualifiedName);
        String displayQualifiedName = displayQualifiedFunctionName(qualifiedName);
        placeholder.setQualifiedName(displayQualifiedName);
        placeholder.setIsPlaceholder(true);
        placeholder.setLanguage(language);
        
        // 从 qualifiedName 中提取 name 和 signature
        // 格式：com.example.Class.method(params):returnType 或 com.example.Class.method(params)
        String nameAndSignature = extractMethodNameAndSignature(displayQualifiedName);
        if (nameAndSignature != null) {
            // 如果包含参数，提取方法名
            int leftParen = nameAndSignature.indexOf('(');
            if (leftParen > 0) {
                String methodName = nameAndSignature.substring(0, leftParen);
                placeholder.setName(methodName);
                placeholder.setSignature(nameAndSignature);
            } else {
                placeholder.setName(nameAndSignature);
            }
        }
        
        return placeholder;
    }

    private String displayQualifiedFunctionName(String functionId) {
        if (functionId == null || functionId.isBlank()) {
            return functionId;
        }
        String raw = GraphIds.raw(null, functionId);
        int scopedIndex = raw.indexOf(GraphIds.PROJECT_SEPARATOR);
        if (scopedIndex >= 0) {
            raw = raw.substring(scopedIndex + GraphIds.PROJECT_SEPARATOR.length());
        }
        return raw.startsWith("fn:") ? raw.substring("fn:".length()) : raw;
    }
    
    /**
     * 从 qualifiedName 中提取方法名和签名
     * 例如：com.example.Class.method(params) -> method(params)
     */
    private String extractMethodNameAndSignature(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return null;
        }
        
        // 先找到第一个 '('，确定方法签名的开始位置
        int leftParen = qualifiedName.indexOf('(');
        if (leftParen <= 0) {
            // 没有参数列表，直接找最后一个 '.'
            int lastDot = qualifiedName.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < qualifiedName.length() - 1) {
                return qualifiedName.substring(lastDot + 1);
            }
            return qualifiedName;
        }
        
        // 在 '(' 之前的部分找最后一个 '.'
        String beforeParams = qualifiedName.substring(0, leftParen);
        int lastDot = beforeParams.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < beforeParams.length() - 1) {
            // 返回 method(params) 部分
            return qualifiedName.substring(lastDot + 1);
        }
        
        return qualifiedName;
    }
    
    protected void triggerCascadeChanges(CodeGraphContext context, List<String> dependentFiles) {
        if (dependentFiles.isEmpty()) {
            return;
        }
        String language = context.getLanguage() == null || context.getLanguage().isBlank() ? "java" : context.getLanguage();
        
        log.info("开始处理级联变更: dependentCount={}", dependentFiles.size());
        
        // 查询依赖文件的 Git 元信息
        List<FileMetadata> dependentFilesWithMeta = context.getReader().getFindWhoCallsMeWithMeta() != null
            ? context.getReader().getFindWhoCallsMeWithMeta().apply(context.getOldProjectFilePath())
            : new ArrayList<>();
        
        // 过滤掉不需要级联的文件
        List<FileMetadata> filteredMeta = dependentFilesWithMeta.stream()
            .filter(meta -> dependentFiles.contains(meta.getProjectFilePath()))
            .collect(Collectors.toList());
        
        for (FileMetadata fileMeta : filteredMeta) {
            if (context.getSender() != null && context.getSender().getSendEvent() != null) {
                CodeChangeEvent event = new CodeChangeEvent();
                event.setEventId(UUID.randomUUID().toString());
                event.setProjectName(context.getProjectName());
                
                // 级联变更无法获取绝对路径，设为 null
                // 后续通过 Git 信息拉取代码获得绝对路径
                event.setAbsoluteFilePath(null);
                
                // 设置 Git 信息
                event.setGitRepoUrl(fileMeta.getGitRepoUrl());
                event.setGitBranch(fileMeta.getGitBranch());
                
                event.setClasspathEntries(context.getClasspathEntries());
                event.setSourcepathEntries(context.getSourcepathEntries());
                event.setOldFileIdentifier(fileMeta.getProjectFilePath());
                event.setChangeType(ChangeType.CASCADE_UPDATE);
                event.setLanguage(language);
                event.setTimestamp(LocalDateTime.now());
                event.setReason("Cascade update from " + context.getOldProjectFilePath());
                
                context.getSender().getSendEvent().accept(event);
                log.debug("已发送级联变更事件: file={}, gitRepo={}, gitBranch={}", 
                    fileMeta.getProjectFilePath(), fileMeta.getGitRepoUrl(), fileMeta.getGitBranch());
            } else {
                log.warn("未配置事件发送器，忽略级联变更: {}", fileMeta.getProjectFilePath());
            }
        }
    }
    
}
