package com.poseidon.codegraph.engine.application.model;

import lombok.Data;

/**
 * 代码端点数据对象
 */
@Data
public class CodeEndpointDO {
    
    // ===== 基础属性（继承自 CodeNode） =====
    private String id;
    private String name;
    private String qualifiedName;
    private String projectFilePath;
    private String gitRepoUrl;
    private String gitBranch;
    private String language;
    private String projectName;
    private Integer startLine;
    private Integer endLine;
    
    // ===== 端点特有属性 =====
    private String endpointType;      // HTTP, KAFKA, REDIS, DB
    private String direction;         // inbound / outbound
    private Boolean isExternal;
    
    // HTTP 相关
    private String httpMethod;
    private String path;
    private String normalizedPath;
    
    // MQ 相关
    private String topic;
    private String operation;
    private String brokerType; // KAFKA, ROCKETMQ
    
    // Redis 相关
    private String keyPattern;
    private String command;
    private String dataStructure;
    
    // DB 相关
    private String tableName;
    private String dbOperation;
    
    // 通用属性
    private String serviceName;
    private String parseLevel;
    private String targetService;
    private String matchIdentity;
}
