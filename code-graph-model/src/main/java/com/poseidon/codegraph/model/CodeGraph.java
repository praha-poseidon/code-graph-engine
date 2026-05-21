package com.poseidon.codegraph.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码图（解析结果容器）
 * 用于存放单次解析的结果（可以是单个文件、多个文件或整个项目）
 */
@Data
public class CodeGraph {
    /**
     * 包列表
     */
    private List<CodePackage> packages = new ArrayList<>();
    
    /**
     * 代码单元列表
     */
    private List<CodeUnit> units = new ArrayList<>();
    
    /**
     * 函数列表
     */
    private List<CodeFunction> functions = new ArrayList<>();
    
    /**
     * 关系列表（包括调用关系、结构关系等）
     */
    private List<CodeRelationship> relationships = new ArrayList<>();
    
    /**
     * 端点列表（HTTP API、Kafka、Redis、DB 等外部交互点）
     */
    private List<CodeEndpoint> endpoints = new ArrayList<>();
    
    /**
     * 添加包
     */
    public void addPackage(CodePackage pkg) {
        if (this.packages == null) {
            this.packages = new ArrayList<>();
        }
        this.packages.add(pkg);
    }
    
    /**
     * 添加代码单元
     */
    public void addUnit(CodeUnit unit) {
        if (this.units == null) {
            this.units = new ArrayList<>();
        }
        this.units.add(unit);
    }
    
    /**
     * 添加函数
     */
    public void addFunction(CodeFunction function) {
        if (this.functions == null) {
            this.functions = new ArrayList<>();
        }
        this.functions.add(function);
    }
    
    /**
     * 添加关系（包括调用关系、结构关系等）
     */
    public void addRelationship(CodeRelationship relationship) {
        if (this.relationships == null) {
            this.relationships = new ArrayList<>();
        }
        this.relationships.add(relationship);
    }
    
    /**
     * 添加端点
     */
    public void addEndpoint(CodeEndpoint endpoint) {
        if (this.endpoints == null) {
            this.endpoints = new ArrayList<>();
        }
        this.endpoints.add(endpoint);
    }
    
    /**
     * 获取包列表
     */
    public List<CodePackage> getPackagesAsList() {
        return this.packages != null ? this.packages : new ArrayList<>();
    }
    
    /**
     * 获取代码单元列表
     */
    public List<CodeUnit> getUnitsAsList() {
        return this.units != null ? this.units : new ArrayList<>();
    }
    
    /**
     * 获取函数列表
     */
    public List<CodeFunction> getFunctionsAsList() {
        return this.functions != null ? this.functions : new ArrayList<>();
    }
    
    /**
     * 获取关系列表（包括调用关系、结构关系等）
     */
    public List<CodeRelationship> getRelationshipsAsList() {
        return this.relationships != null ? this.relationships : new ArrayList<>();
    }
    
    /**
     * 获取端点列表
     */
    public List<CodeEndpoint> getEndpointsAsList() {
        return this.endpoints != null ? this.endpoints : new ArrayList<>();
    }
}

