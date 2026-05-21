package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;

import java.util.List;
import java.util.Set;

/**
 * 端点仓储接口
 */
public interface CodeEndpointRepository {
    
    /**
     * 批量插入端点
     */
    void insertEndpointsBatch(List<CodeEndpointDO> endpoints);
    
    /**
     * 批量更新端点
     */
    void updateEndpointsBatch(List<CodeEndpointDO> endpoints);
    
    /**
     * 根据 ID 删除端点
     */
    void deleteById(String projectName, String id);
    
    /**
     * 查询哪些端点已存在
     * @param ids 端点ID列表
     * @return 已存在的端点ID集合
     */
    Set<String> findExistingEndpointsByIds(String projectName, List<String> ids);
    
    /**
     * 根据项目文件路径查找端点
     */
    List<CodeEndpointDO> findEndpointsByProjectFilePath(String projectName, String projectFilePath);

    /**
     * 根据方向查找项目端点。
     */
    List<CodeEndpointDO> findEndpointsByDirection(String projectName, String direction);
    
    /**
     * 根据 matchIdentity 查找所有端点（可选指定 direction）
     * @param matchIdentity 匹配标识
     * @param direction 方向（inbound/outbound），null 表示查询所有方向
     * @return 匹配的端点列表
     */
    List<CodeEndpointDO> findEndpointsByMatchIdentity(String matchIdentity, String direction);
}
