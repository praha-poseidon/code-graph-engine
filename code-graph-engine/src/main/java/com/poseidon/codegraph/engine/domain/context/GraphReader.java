package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.FileMetadata;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 图谱读取器
 * 聚合所有查询相关的函数
 */
@Data
public class GraphReader {
    
    /**
     * 查找谁依赖我（入边）
     * Input: projectFilePath -> Output: List<dependentProjectFilePath>
     */
    private Function<String, List<String>> findWhoCallsMe;
    
    /**
     * 查找谁依赖我（带 Git 元信息）
     * Input: projectFilePath -> Output: List<FileMetadata>
     */
    private Function<String, List<FileMetadata>> findWhoCallsMeWithMeta;
    
    /**
     * 查找文件的所有代码单元
     * Input: projectFilePath -> Output: List<CodeUnit>
     */
    private Function<String, List<CodeUnit>> findUnitsByProjectFilePath;
    
    /**
     * 查找文件的所有函数
     * Input: projectFilePath -> Output: List<CodeFunction>
     */
    private Function<String, List<CodeFunction>> findFunctionsByProjectFilePath;
    
    /**
     * 批量查询函数是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingFunctionsByQualifiedNames;

    /**
     * 批量查询函数详情
     * Input: List<qualifiedName> -> Output: Map<qualifiedName, CodeFunction>
     */
    private Function<java.util.List<String>, java.util.Map<String, CodeFunction>> findFunctionsByQualifiedNames;
    
    /**
     * 批量查询单元是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingUnitsByQualifiedNames;
    
    /**
     * 批量查询包是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingPackagesByQualifiedNames;
    
    /**
     * 批量查询结构关系是否存在
     * Input: List<CodeRelationshipDO> -> Output: Set<relationshipKey>
     * relationshipKey 格式：fromNodeId:toNodeId:relType
     */
    private Function<java.util.List<com.poseidon.codegraph.engine.application.model.CodeRelationshipDO>, java.util.Set<String>> findExistingStructureRelationships;
    
    /**
     * 查询已存在的端点
     * 输入：端点ID列表，输出：已存在的端点ID集合
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingEndpointsByIds;
    
    /**
     * 查找文件的所有端点
     * Input: projectFilePath -> Output: List<CodeEndpoint>
     */
    private Function<String, java.util.List<com.poseidon.codegraph.model.CodeEndpoint>> findEndpointsByProjectFilePath;
    
    /**
     * 根据 matchIdentity 和 direction 查找端点
     * Input: (matchIdentity, direction) -> Output: List<CodeEndpoint>
     */
    private java.util.function.BiFunction<String, String, java.util.List<com.poseidon.codegraph.model.CodeEndpoint>> findEndpointsByMatchIdentity;
}
