package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodeUnitDO;

import java.util.List;
import java.util.Set;

/**
 * 代码单元仓储接口
 */
public interface CodeUnitRepository {

    /**
     * 根据文件路径查找所有代码单元
     */
    List<CodeUnitDO> findUnitsByProjectFilePath(String projectName, String projectFilePath);

    /**
     * 批量查询单元是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    Set<String> findExistingUnitsByQualifiedNames(String projectName, List<String> qualifiedNames);

    /**
     * 批量插入单元（纯数据库操作，不做存在性检查）
     */
    void insertUnitsBatch(List<CodeUnitDO> units);

    /**
     * 批量更新单元（纯数据库操作，不做存在性检查）
     */
    void updateUnitsBatch(List<CodeUnitDO> units);
    
    /**
     * 根据 ID 删除单元
     * @param id 单元 ID
     */
    void deleteById(String projectName, String id);
}
