package com.poseidon.codegraph.engine.application.converter;

import com.poseidon.codegraph.engine.application.model.*;
import com.poseidon.codegraph.model.*;
import com.poseidon.codegraph.model.endpoint.*;

/**
 * 代码图谱转换器
 * 负责 DO（应用层）和领域模型（领域层）之间的转换
 */
public class CodeGraphConverter {
    
    // ========== DO -> 领域模型 ==========
    
    public static CodePackage toDomain(CodePackageDO dobj) {
        if (dobj == null) return null;
        
        CodePackage domain = new CodePackage();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectName(dobj.getProjectName());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setPackagePath(dobj.getPackagePath());
        return domain;
    }
    
    public static CodeUnit toDomain(CodeUnitDO dobj) {
        if (dobj == null) return null;
        
        CodeUnit domain = new CodeUnit();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectName(dobj.getProjectName());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setGitRepoUrl(dobj.getGitRepoUrl());
        domain.setGitBranch(dobj.getGitBranch());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setUnitType(dobj.getUnitType());
        domain.setModifiers(dobj.getModifiers());
        domain.setIsAbstract(dobj.getIsAbstract());
        domain.setPackageId(dobj.getPackageId());
        return domain;
    }
    
    public static CodeFunction toDomain(CodeFunctionDO dobj) {
        if (dobj == null) return null;
        
        CodeFunction domain = new CodeFunction();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectName(dobj.getProjectName());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setGitRepoUrl(dobj.getGitRepoUrl());
        domain.setGitBranch(dobj.getGitBranch());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setSignature(dobj.getSignature());
        domain.setReturnType(dobj.getReturnType());
        domain.setModifiers(dobj.getModifiers());
        domain.setIsStatic(dobj.getIsStatic());
        domain.setIsAsync(dobj.getIsAsync());
        domain.setIsConstructor(dobj.getIsConstructor());
        domain.setIsPlaceholder(dobj.getIsPlaceholder());
        return domain;
    }
    
    public static CodeRelationship toDomain(CodeRelationshipDO dobj) {
        if (dobj == null) return null;
        
        CodeRelationship domain = new CodeRelationship();
        domain.setId(dobj.getId());
        domain.setFromNodeId(dobj.getFromNodeId());
        domain.setToNodeId(dobj.getToNodeId());
        
        // 关系类型
        if (dobj.getRelationshipType() != null) {
            domain.setRelationshipType(RelationshipType.valueOf(dobj.getRelationshipType()));
        }
        
        domain.setLineNumber(dobj.getLineNumber());
        domain.setCallType(dobj.getCallType());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectName(dobj.getProjectName());
        return domain;
    }
    
    public static CodeEndpoint toDomain(CodeEndpointDO dobj) {
        if (dobj == null) return null;
        
        // 1. 确定端点类型
        EndpointType type = EndpointType.UNKNOWN;
        if (dobj.getEndpointType() != null) {
            try {
                type = EndpointType.valueOf(dobj.getEndpointType());
            } catch (IllegalArgumentException e) {
                // 忽略未知类型
            }
        }
        
        // 2. 创建正确的子类实例
        CodeEndpoint domain;
        switch (type) {
            case HTTP:
                HttpEndpoint http = new HttpEndpoint();
                http.setHttpMethod(dobj.getHttpMethod());
                http.setPath(dobj.getPath());
                http.setNormalizedPath(dobj.getNormalizedPath());
                domain = http;
                break;
            case MQ:
                MqEndpoint mq = new MqEndpoint();
                mq.setTopic(dobj.getTopic());
                mq.setOperation(dobj.getOperation());
                mq.setBrokerType(dobj.getBrokerType());
                domain = mq;
                break;
            case REDIS:
                RedisEndpoint redis = new RedisEndpoint();
                redis.setKeyPattern(dobj.getKeyPattern());
                redis.setDataStructure(dobj.getDataStructure());
                redis.setCommand(dobj.getCommand());
                domain = redis;
                break;
            case DB:
                DbEndpoint db = new DbEndpoint();
                db.setTableName(dobj.getTableName());
                db.setDbOperation(dobj.getDbOperation());
                domain = db;
                break;
            default:
                // 兜底方案
                HttpEndpoint fallback = new HttpEndpoint();
                fallback.setEndpointType(EndpointType.UNKNOWN);
                domain = fallback;
        }
        
        // 3. 填充公共字段
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setGitRepoUrl(dobj.getGitRepoUrl());
        domain.setGitBranch(dobj.getGitBranch());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectName(dobj.getProjectName());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setDirection(dobj.getDirection());
        domain.setIsExternal(dobj.getIsExternal());
        domain.setServiceName(dobj.getServiceName());
        domain.setParseLevel(dobj.getParseLevel());
        domain.setTargetService(dobj.getTargetService());
        domain.setMatchIdentity(dobj.getMatchIdentity());
        
        return domain;
    }
    
    // ========== 领域模型 -> DO ==========
    
    public static CodePackageDO toDO(CodePackage domain) {
        if (domain == null) return null;
        
        CodePackageDO dobj = new CodePackageDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectName(domain.getProjectName());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setPackagePath(domain.getPackagePath());
        return dobj;
    }
    
    public static CodeUnitDO toDO(CodeUnit domain) {
        if (domain == null) return null;
        
        CodeUnitDO dobj = new CodeUnitDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectName(domain.getProjectName());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setGitRepoUrl(domain.getGitRepoUrl());
        dobj.setGitBranch(domain.getGitBranch());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        dobj.setUnitType(domain.getUnitType());
        dobj.setModifiers(domain.getModifiers());
        dobj.setIsAbstract(domain.getIsAbstract());
        dobj.setPackageId(domain.getPackageId());
        return dobj;
    }
    
    public static CodeFunctionDO toDO(CodeFunction domain) {
        if (domain == null) return null;
        
        CodeFunctionDO dobj = new CodeFunctionDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectName(domain.getProjectName());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setGitRepoUrl(domain.getGitRepoUrl());
        dobj.setGitBranch(domain.getGitBranch());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        dobj.setSignature(domain.getSignature());
        dobj.setReturnType(domain.getReturnType());
        dobj.setModifiers(domain.getModifiers());
        dobj.setIsStatic(domain.getIsStatic());
        dobj.setIsAsync(domain.getIsAsync());
        dobj.setIsConstructor(domain.getIsConstructor());
        dobj.setIsPlaceholder(domain.getIsPlaceholder());
        return dobj;
    }
    
    public static CodeRelationshipDO toDO(CodeRelationship domain) {
        if (domain == null) return null;
        
        CodeRelationshipDO dobj = new CodeRelationshipDO();
        dobj.setId(domain.getId());
        dobj.setFromNodeId(domain.getFromNodeId());
        dobj.setToNodeId(domain.getToNodeId());
        
        // 关系类型
        if (domain.getRelationshipType() != null) {
            dobj.setRelationshipType(domain.getRelationshipType().name());
        }
        
        dobj.setLineNumber(domain.getLineNumber());
        dobj.setCallType(domain.getCallType());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectName(domain.getProjectName());
        return dobj;
    }
    
    public static CodeEndpointDO toDO(CodeEndpoint domain) {
        if (domain == null) return null;
        
        CodeEndpointDO dobj = new CodeEndpointDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setGitRepoUrl(domain.getGitRepoUrl());
        dobj.setGitBranch(domain.getGitBranch());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectName(domain.getProjectName());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        
        // 设置端点类型字符串
        if (domain.getEndpointType() != null) {
            dobj.setEndpointType(domain.getEndpointType().name());
        }
        
        dobj.setDirection(domain.getDirection());
        dobj.setIsExternal(domain.getIsExternal());
        dobj.setServiceName(domain.getServiceName());
        dobj.setParseLevel(domain.getParseLevel());
        dobj.setTargetService(domain.getTargetService());
        dobj.setMatchIdentity(domain.getMatchIdentity());
        
        // 根据子类类型设置特定字段
        if (domain instanceof HttpEndpoint) {
            HttpEndpoint http = (HttpEndpoint) domain;
            dobj.setHttpMethod(http.getHttpMethod());
            dobj.setPath(http.getPath());
            dobj.setNormalizedPath(http.getNormalizedPath());
        } else if (domain instanceof MqEndpoint) {
            MqEndpoint mq = (MqEndpoint) domain;
            dobj.setTopic(mq.getTopic());
            dobj.setOperation(mq.getOperation());
            dobj.setBrokerType(mq.getBrokerType());
        } else if (domain instanceof RedisEndpoint) {
            RedisEndpoint redis = (RedisEndpoint) domain;
            dobj.setKeyPattern(redis.getKeyPattern());
            dobj.setDataStructure(redis.getDataStructure());
            dobj.setCommand(redis.getCommand());
        } else if (domain instanceof DbEndpoint) {
            DbEndpoint db = (DbEndpoint) domain;
            dobj.setTableName(db.getTableName());
            dobj.setDbOperation(db.getDbOperation());
        }
        
        return dobj;
    }
}
