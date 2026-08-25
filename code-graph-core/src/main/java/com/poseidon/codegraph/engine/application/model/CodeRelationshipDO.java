package com.poseidon.codegraph.engine.application.model;

import lombok.Data;

/**
 * 代码关系数据对象（应用层）
 * 包括调用关系、结构关系等
 */
@Data
public class CodeRelationshipDO {
    private String id;
    
    /**
     * 源节点 ID（通用字段）
     */
    private String fromNodeId;
    
    /**
     * 目标节点 ID（通用字段）
     */
    private String toNodeId;
    
    /**
     * 持久化的原生关系名，包括公共类型和语言自有类型。
     */
    private String relationshipType;

    /** Endpoint node labels supplied by the parser relationship contract. */
    private String fromNodeType;
    private String toNodeType;
    
    /**
     * 调用位置行号（仅用于 CALLS 关系）
     */
    private Integer lineNumber;
    
    /**
     * 调用类型：static, virtual, direct（仅用于 CALLS 关系）
     */
    private String callType;
    
    /**
     * 语言
     */
    private String language;

    /**
     * 所属项目名称。
     */
    private String projectName;
}
