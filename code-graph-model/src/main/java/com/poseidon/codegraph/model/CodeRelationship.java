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
     * Language-neutral behavior used by Engine services. For declared shared
     * types this defaults from {@link #relationshipType}; language-specific
     * types must provide it in GraphDelta.
     */
    private RelationshipKind relationshipKind;

    /** Parser-declared endpoint node contracts (Neo4j labels). */
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

    public RelationshipKind getRelationshipKind() {
        if (relationshipKind != null) {
            return relationshipKind;
        }
        return relationshipType == null ? null : relationshipType.getDefaultKind();
    }

    public String getFromNodeType() {
        if (fromNodeType != null && !fromNodeType.isBlank()) {
            return fromNodeType;
        }
        return relationshipType == null ? null : relationshipType.getFromLabel();
    }

    public String getToNodeType() {
        if (toNodeType != null && !toNodeType.isBlank()) {
            return toNodeType;
        }
        return relationshipType == null ? null : relationshipType.getToLabel();
    }

    public boolean hasKind(RelationshipKind kind) {
        return kind != null && kind == getRelationshipKind();
    }
}
