package com.poseidon.codegraph.starter.service;

import com.poseidon.codegraph.engine.application.converter.CodeGraphConverter;
import com.poseidon.codegraph.engine.application.repository.*;
import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.service.CodeGraphService;
import com.poseidon.codegraph.spi.CodeGraphParserRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量更新服务（应用层）
 * 职责：
 * 1. 构建变更上下文（Data + Reader/Writer）
 * 2. 调用领域服务
 */
@Slf4j
public class IncrementalUpdateService {
    
    private final CodeGraphService codeGraphService;
    private final CodePackageRepository packageRepository;
    private final CodeUnitRepository unitRepository;
    private final CodeFunctionRepository functionRepository;
    private final CodeRelationshipRepository relationshipRepository;
    private final CodeEndpointRepository endpointRepository;
    private final CodeGraphParserRegistry parserRegistry;
    
    public IncrementalUpdateService(
            CodePackageRepository packageRepository,
            CodeUnitRepository unitRepository,
            CodeFunctionRepository functionRepository,
            CodeRelationshipRepository relationshipRepository,
            CodeEndpointRepository endpointRepository) {
        this.codeGraphService = new CodeGraphService();
        this.packageRepository = packageRepository;
        this.unitRepository = unitRepository;
        this.functionRepository = functionRepository;
        this.relationshipRepository = relationshipRepository;
        this.endpointRepository = endpointRepository;
        this.parserRegistry = CodeGraphParserRegistry.loadFromServiceLoader();
        
        log.info("IncrementalUpdateService 初始化完成，已加载解析器: {}", parserRegistry.languages());
    }
    
    /**
     * 处理文件变更（通用入口）
     */
    public void handleFileChange(String projectName, String absoluteFilePath, String projectFilePath,
                                 String gitRepoUrl, String gitBranch,
                                 String[] classpathEntries, String[] sourcepathEntries,
                                 boolean isCascade) {
        handleFileChange(
            projectName,
            absoluteFilePath,
            projectFilePath,
            gitRepoUrl,
            gitBranch,
            classpathEntries,
            sourcepathEntries,
            isCascade,
            null,
            null);
    }

    /**
     * 处理文件变更，并使用外部传入的 SER 规则文本。
     */
    public void handleFileChange(String projectName, String absoluteFilePath, String projectFilePath,
                                 String gitRepoUrl, String gitBranch,
                                 String[] classpathEntries, String[] sourcepathEntries,
                                 boolean isCascade,
                                 List<String> endpointRuleSources,
                                 List<String> traceRuleSources) {
        CodeGraphContext context = buildContext(
            projectName,
            absoluteFilePath,
            projectFilePath,
            gitRepoUrl,
            gitBranch,
            classpathEntries,
            sourcepathEntries,
            endpointRuleSources,
            traceRuleSources);
        
        if (isCascade) {
            context.setChangeType(ChangeType.CASCADE_UPDATE);
            // 级联更新时，传入的 projectFilePath 是触发变更的文件（可能是旧文件）
            context.setOldProjectFilePath(projectFilePath);
            // 级联变更通常是由于其他文件变更导致的，当前文件本身可能没有位置变化，所以 NewProjectFilePath 也可以设为 projectFilePath
            // 但具体逻辑取决于级联更新的处理方式，这里暂时只设置 OldProjectFilePath 作为标识
        } else {
            // 默认视为修改，或者需要调用方明确传入 ChangeType
            // 但在此简化方法中，暂且设为 MODIFIED，如果是新增/删除应该走专用方法
            context.setChangeType(ChangeType.SOURCE_MODIFIED); 
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(projectFilePath);
        }
        
        codeGraphService.handle(context);
    }
    
    /**
     * 处理文件新增
     */
    public void handleFileAdded(String projectName, String absoluteFilePath, String projectFilePath,
                                String gitRepoUrl, String gitBranch,
                                String[] classpathEntries, String[] sourcepathEntries) {
        handleFileAdded(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
                classpathEntries, sourcepathEntries, null, null);
    }

    public void handleFileAdded(String projectName, String absoluteFilePath, String projectFilePath,
                                String gitRepoUrl, String gitBranch,
                                String[] classpathEntries, String[] sourcepathEntries,
                                List<String> endpointRuleSources,
                                List<String> traceRuleSources) {
        log.info("处理文件新增: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
                    classpathEntries, sourcepathEntries, endpointRuleSources, traceRuleSources);
            context.setChangeType(ChangeType.SOURCE_ADDED);
            context.setOldProjectFilePath(null);
            context.setNewProjectFilePath(projectFilePath);
            
            codeGraphService.handle(context);
            log.info("文件新增处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件新增处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件新增失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 处理文件删除
     */
    public void handleFileDeleted(String projectName, String absoluteFilePath, String projectFilePath,
                                  String gitRepoUrl, String gitBranch,
                                  String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件删除: absolutePath={}, projectPath={}", absoluteFilePath, projectFilePath);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch, classpathEntries, sourcepathEntries);
            context.setChangeType(ChangeType.SOURCE_DELETED);
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(null);
            
            codeGraphService.handle(context);
            log.info("文件删除处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件删除处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件删除失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 处理文件修改
     */
    public void handleFileModified(String projectName, String absoluteFilePath, String projectFilePath,
                                   String gitRepoUrl, String gitBranch,
                                   String[] classpathEntries, String[] sourcepathEntries) {
        handleFileModified(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
                classpathEntries, sourcepathEntries, null, null);
    }

    public void handleFileModified(String projectName, String absoluteFilePath, String projectFilePath,
                                   String gitRepoUrl, String gitBranch,
                                   String[] classpathEntries, String[] sourcepathEntries,
                                   List<String> endpointRuleSources,
                                   List<String> traceRuleSources) {
        log.info("处理文件修改: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
                    classpathEntries, sourcepathEntries, endpointRuleSources, traceRuleSources);
            context.setChangeType(ChangeType.SOURCE_MODIFIED);
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(projectFilePath);
            
            codeGraphService.handle(context);
            log.info("文件修改处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件修改处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件修改失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 构建上下文（注入 Repository 实现）
     */
    private CodeGraphContext buildContext(String projectName, String absoluteFilePath, String projectFilePath,
                                          String gitRepoUrl, String gitBranch,
                                          String[] classpathEntries, String[] sourcepathEntries) {
        return buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch,
                classpathEntries, sourcepathEntries, null, null);
    }

    private CodeGraphContext buildContext(String projectName, String absoluteFilePath, String projectFilePath,
                                          String gitRepoUrl, String gitBranch,
                                          String[] classpathEntries, String[] sourcepathEntries,
                                          List<String> endpointRuleSources,
                                          List<String> traceRuleSources) {
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName(projectName);
        context.setAbsoluteFilePath(absoluteFilePath);
        context.setProjectFilePath(projectFilePath);
        context.setGitRepoUrl(gitRepoUrl);
        context.setGitBranch(gitBranch);
        context.setClasspathEntries(classpathEntries);
        context.setSourcepathEntries(sourcepathEntries);
        context.setLanguage(inferLanguage(projectFilePath));
        context.setEndpointRuleSources(endpointRuleSources);
        context.setTraceRuleSources(traceRuleSources);
        context.setParserRegistry(parserRegistry);
        
        // ========== 查询函数 (Reader) ==========
        
        context.getReader().setFindWhoCallsMe(path -> 
            relationshipRepository.findWhoCallsMe(projectName, path)
        );
        
        context.getReader().setFindWhoCallsMeWithMeta(path -> 
            relationshipRepository.findWhoCallsMeWithMeta(projectName, path).stream()
                .map(this::fileMetaInfoToMetadata)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindUnitsByProjectFilePath(path -> 
            unitRepository.findUnitsByProjectFilePath(projectName, path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindFunctionsByProjectFilePath(path -> 
            functionRepository.findFunctionsByProjectFilePath(projectName, path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindExistingFunctionsByQualifiedNames(qualifiedNames -> 
            functionRepository.findExistingFunctionsByQualifiedNames(projectName, qualifiedNames)
        );

        context.getReader().setFindFunctionsByQualifiedNames(qualifiedNames ->
            functionRepository.findFunctionsByQualifiedNames(projectName, qualifiedNames).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toMap(
                    com.poseidon.codegraph.model.CodeFunction::getId,
                    function -> function,
                    (left, right) -> left
                ))
        );
        
        context.getReader().setFindExistingUnitsByQualifiedNames(qualifiedNames -> 
            unitRepository.findExistingUnitsByQualifiedNames(projectName, qualifiedNames)
        );
        
        context.getReader().setFindExistingPackagesByQualifiedNames(qualifiedNames -> 
            packageRepository.findExistingPackagesByQualifiedNames(projectName, qualifiedNames)
        );
        
        context.getReader().setFindExistingStructureRelationships(relationships -> 
            relationshipRepository.findExistingStructureRelationships(projectName, relationships)
        );
        
        context.getReader().setFindExistingEndpointsByIds(ids -> 
            endpointRepository.findExistingEndpointsByIds(projectName, ids)
        );
        
        context.getReader().setFindEndpointsByProjectFilePath(path -> 
            endpointRepository.findEndpointsByProjectFilePath(projectName, path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindEndpointsByMatchIdentity((matchIdentity, direction) -> 
            endpointRepository.findEndpointsByMatchIdentity(matchIdentity, direction).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        // ========== 修改函数 (Writer) ==========
        
        context.getWriter().setDeleteFileOutgoingCalls(path -> 
            relationshipRepository.deleteFileOutgoingCalls(projectName, path)
        );
        
        context.getWriter().setDeleteNode(nodeId -> {
            unitRepository.deleteById(projectName, nodeId);
            functionRepository.deleteById(projectName, nodeId);
            endpointRepository.deleteById(projectName, nodeId);
        });

        context.getWriter().setDeleteRelationship(relationshipId ->
            relationshipRepository.deleteById(projectName, relationshipId)
        );
        
        // ========== 批量插入函数 ==========
        
        context.getWriter().setInsertPackagesBatch(packages -> 
            packageRepository.insertPackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertUnitsBatch(units -> 
            unitRepository.insertUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertFunctionsBatch(functions -> 
            functionRepository.insertFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertRelationshipsBatch(relationships -> 
            relationshipRepository.insertRelationshipsBatch(
                relationships.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        // ========== 批量更新函数 ==========
        
        context.getWriter().setUpdatePackagesBatch(packages -> 
            packageRepository.updatePackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateUnitsBatch(units -> 
            unitRepository.updateUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateFunctionsBatch(functions -> 
            functionRepository.updateFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertEndpointsBatch(endpoints -> 
            endpointRepository.insertEndpointsBatch(
                endpoints.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateEndpointsBatch(endpoints -> 
            endpointRepository.updateEndpointsBatch(
                endpoints.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        // ========== 事件发送 ==========
        
        context.getSender().setSendEvent(event -> {
            // 收到代码变更事件（CASCADE） -> 重新调用 handleFileChange
            // 注意：这里是同步调用，实际生产环境建议发送 MQ 消息或 Spring Event
            this.handleFileChange(
                event.getProjectName(),
                event.getAbsoluteFilePath(),
                event.getOldFileIdentifier(), // 级联变更的目标文件
                event.getGitRepoUrl(),
                event.getGitBranch(),
                event.getClasspathEntries(),
                event.getSourcepathEntries(),
                true // isCascade = true
            );
        });
        
        return context;
    }

    private String inferLanguage(String projectFilePath) {
        if (projectFilePath == null) {
            return "java";
        }
        String path = projectFilePath.toLowerCase();
        if (path.endsWith(".java")) {
            return "java";
        }
        if (path.endsWith(".go")) {
            return "go";
        }
        if (path.endsWith(".py")) {
            return "python";
        }
        if (path.endsWith(".js") || path.endsWith(".jsx")) {
            return "javascript";
        }
        if (path.endsWith(".ts") || path.endsWith(".tsx")) {
            return "typescript";
        }
        return "java";
    }
    
    /**
     * 将应用层的 FileMetaInfo 转换为领域层的 FileMetadata
     */
    private com.poseidon.codegraph.model.FileMetadata fileMetaInfoToMetadata(
            com.poseidon.codegraph.engine.application.model.FileMetaInfo metaInfo) {
        com.poseidon.codegraph.model.FileMetadata metadata = 
            new com.poseidon.codegraph.model.FileMetadata();
        metadata.setProjectFilePath(metaInfo.getProjectFilePath());
        metadata.setGitRepoUrl(metaInfo.getGitRepoUrl());
        metadata.setGitBranch(metaInfo.getGitBranch());
        return metadata;
    }
}
