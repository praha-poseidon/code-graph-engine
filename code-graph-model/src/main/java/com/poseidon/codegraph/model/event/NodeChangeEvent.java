package com.poseidon.codegraph.model.event;

import com.poseidon.codegraph.model.CodeNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点变更事件（领域事件）
 * 针对单个节点（包/单元/函数/端点）的增删改事件
 */
@Data
public class NodeChangeEvent {
    /**
     * 事件 ID
     */
    private String eventId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目文件路径
     */
    private String projectFilePath;

    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;

    /**
     * Git 分支名
     */
    private String gitBranch;

    /**
     * 节点类型
     * 删除事件中该字段可能为空（只知道节点 ID）
     */
    private NodeType nodeType;

    /**
     * 变更操作类型
     */
    private NodeOperation operation;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 节点对象
     * 删除事件中该字段为空
     */
    private CodeNode node;

    /**
     * 语言
     */
    private String language;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;
}
