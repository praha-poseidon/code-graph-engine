package com.poseidon.codegraph.model;

import lombok.Data;

/**
 * 代码关系
 * 表示代码元素之间的各种关系
 */
@Data
public class CodeRelationship {
    /**
     * 关系 ID
     */
    private String id;
    
    /**
     * 源节点 ID（起始节点）
     * 对于 CALLS: 调用方函数 ID
     * 对于 PACKAGE_TO_UNIT: 包 ID
     * 对于 UNIT_TO_FUNCTION: 单元 ID
     */
    private String fromNodeId;
    
    /**
     * 目标节点 ID（终止节点）
     * 对于 CALLS: 被调用方函数 ID
     * 对于 PACKAGE_TO_UNIT: 单元 ID
     * 对于 UNIT_TO_FUNCTION: 函数 ID
     */
    private String toNodeId;
    
    /**
     * 关系类型
     */
    private RelationshipType relationshipType;
    
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
