package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.FileMetaInfo;

import java.util.List;

/**
 * 代码关系仓储接口
 */
public interface CodeRelationshipRepository {

    /**
     * 查找谁依赖我（入边）
     * 
     * @param targetProjectFilePath 目标文件路径
     * @return 依赖该文件的文件路径列表
     */
    List<String> findWhoCallsMe(String projectName, String targetProjectFilePath);

    /**
     * 查找谁依赖我（带 Git 元信息）
     * 用于级联变更时获取依赖文件的完整信息
     * 
     * @param targetProjectFilePath 目标文件路径
     * @return 依赖该文件的文件元信息列表（包含 Git 信息）
     */
    List<FileMetaInfo> findWhoCallsMeWithMeta(String projectName, String targetProjectFilePath);

    /**
     * 删除文件的出边（该文件发起的调用）
     * 
     * @param projectFilePath 文件路径
     */
    void deleteFileOutgoingCalls(String projectName, String projectFilePath);

    /**
     * 删除关系。
     *
     * @param relationshipId 关系 ID
     */
    void deleteById(String projectName, String relationshipId);

    /**
     * 批量插入关系（包括调用关系、结构关系等）（纯数据库操作，不做存在性检查）
     */
    void insertRelationshipsBatch(List<CodeRelationshipDO> relationships);

    /**
     * 查询节点出边。
     */
    List<CodeRelationshipDO> findOutgoingRelationships(String projectName, String nodeId, String relationshipType);

    /**
     * 查询节点入边。
     */
    List<CodeRelationshipDO> findIncomingRelationships(String projectName, String nodeId, String relationshipType);

    /**
     * 查询已存在的结构关系（PACKAGE_TO_UNIT, UNIT_TO_FUNCTION）
     * 
     * @param relationships 待检查的关系列表
     * @return 已存在的关系 key 集合，key 格式为 "fromNodeId:toNodeId:relType"
     */
    java.util.Set<String> findExistingStructureRelationships(String projectName, List<CodeRelationshipDO> relationships);
}
