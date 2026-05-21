package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeUnit;
import lombok.Data;

import java.util.function.Consumer;

/**
 * 图谱写入器
 * 聚合所有增删改相关的函数
 */
@Data
public class GraphWriter {
    
    // ========== 删除函数 ==========
    
    /**
     * 删除文件的出边
     * Input: projectFilePath
     */
    private Consumer<String> deleteFileOutgoingCalls;
    
    /**
     * 删除节点
     * Input: nodeId
     */
    private Consumer<String> deleteNode;

    /**
     * 删除关系
     * Input: relationshipId
     */
    private Consumer<String> deleteRelationship;
    
    // ========== 批量插入函数 ==========
    
    /**
     * 批量插入函数（纯数据库操作）
     */
    private Consumer<java.util.List<CodeFunction>> insertFunctionsBatch;
    
    /**
     * 批量插入单元（纯数据库操作）
     */
    private Consumer<java.util.List<CodeUnit>> insertUnitsBatch;
    
    /**
     * 批量插入包（纯数据库操作）
     */
    private Consumer<java.util.List<CodePackage>> insertPackagesBatch;
    
    /**
     * 批量插入关系（包括调用关系、结构关系等）（纯数据库操作）
     */
    private Consumer<java.util.List<CodeRelationship>> insertRelationshipsBatch;
    
    // ========== 批量更新函数 ==========
    
    /**
     * 批量更新函数（纯数据库操作）
     */
    private Consumer<java.util.List<CodeFunction>> updateFunctionsBatch;
    
    /**
     * 批量更新单元（纯数据库操作）
     */
    private Consumer<java.util.List<CodeUnit>> updateUnitsBatch;
    
    /**
     * 批量更新包（纯数据库操作）
     */
    private Consumer<java.util.List<CodePackage>> updatePackagesBatch;
    
    // ========== 端点操作 ==========
    
    /**
     * 批量插入端点（纯数据库操作）
     */
    private Consumer<java.util.List<CodeEndpoint>> insertEndpointsBatch;
    
    /**
     * 批量更新端点（纯数据库操作）
     */
    private Consumer<java.util.List<CodeEndpoint>> updateEndpointsBatch;
}
