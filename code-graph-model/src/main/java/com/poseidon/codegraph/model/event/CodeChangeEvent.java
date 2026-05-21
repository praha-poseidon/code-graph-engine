package com.poseidon.codegraph.model.event;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 代码变更事件（领域事件）
 * 纯粹的代码图谱概念，不包含 Git、Webhook 等技术细节
 */
@Data
public class CodeChangeEvent {
    /**
     * 事件 ID
     */
    private String eventId;
    
    /**
     * 项目名称
     */
    private String projectName;
    
    /**
     * 文件绝对路径（用于读取内容）
     */
    private String absoluteFilePath;
    
    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支名
     */
    private String gitBranch;
    
    /**
     * classpath 条目（依赖的 jar 包路径）
     */
    private String[] classpathEntries;
    
    /**
     * sourcepath 条目（源代码目录）
     */
    private String[] sourcepathEntries;
    
    /**
     * 旧文件标识（用于查询依赖、查询旧节点）
     * - 修改：旧文件路径（项目相对路径）
     * - 新增：null
     * - 删除：旧文件路径（项目相对路径）
     */
    private String oldFileIdentifier;
    
    /**
     * 新文件路径（项目相对路径，用于数据库存储）
     * - 修改：新文件路径
     * - 新增：新文件路径
     * - 删除：null
     */
    private String newProjectFilePath;
    
    /**
     * 变更类型
     */
    private ChangeType changeType;
    
    /**
     * 语言
     */
    private String language;
    
    /**
     * 事件时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 变更原因
     */
    private String reason;
}
