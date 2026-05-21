package com.poseidon.codegraph.model;

/**
 * 关系类型枚举
 * 每个关系类型定义了源节点和目标节点的标签，用于 Neo4j 查询和关系创建
 */
public enum RelationshipType {
    /**
     * 函数调用关系
     */
    CALLS("CodeFunction", "CodeFunction"),
    
    /**
     * 包包含单元
     */
    PACKAGE_TO_UNIT("CodePackage", "CodeUnit"),
    
    /**
     * 单元包含函数
     */
    UNIT_TO_FUNCTION("CodeUnit", "CodeFunction"),

    /**
     * 单元继承单元（class extends class、interface extends interface）
     */
    EXTENDS("CodeUnit", "CodeUnit"),

    /**
     * 单元实现接口（class/enum implements interface）
     */
    IMPLEMENTS("CodeUnit", "CodeUnit"),

    /**
     * 函数重写父类或接口函数
     */
    OVERRIDES("CodeFunction", "CodeFunction"),
    
    /**
     * 端点到函数（入站端点，如 HTTP 请求进入某个 Controller 方法）
     */
    ENDPOINT_TO_FUNCTION("CodeEndpoint", "CodeFunction"),
    
    /**
     * 函数到端点（出站端点，如函数调用外部 API）
     */
    FUNCTION_TO_ENDPOINT("CodeFunction", "CodeEndpoint"),
    
    /**
     * 端点匹配关系（跨服务）
     * - 连接 normalizedPath 相同的 outbound 和 inbound endpoint
     * - 方向：outbound -> inbound（从调用方指向提供方）
     * - 用于级联感知：删除/修改一端时可以找到所有匹配的另一端
     * - 注意：不创建 placeholder 端点，只在两端都存在时才创建关系
     */
    MATCHES("CodeEndpoint", "CodeEndpoint");
    
    /**
     * 源节点的 Neo4j 标签
     */
    private final String fromLabel;
    
    /**
     * 目标节点的 Neo4j 标签
     */
    private final String toLabel;
    
    RelationshipType(String fromLabel, String toLabel) {
        this.fromLabel = fromLabel;
        this.toLabel = toLabel;
    }
    
    public String getFromLabel() {
        return fromLabel;
    }
    
    public String getToLabel() {
        return toLabel;
    }
}
