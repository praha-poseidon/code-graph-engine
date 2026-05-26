package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodePackageDO;

import java.util.List;
import java.util.Set;

/**
 * 代码包仓储接口
 */
public interface CodePackageRepository {

    /**
     * 批量查询包是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    Set<String> findExistingPackagesByQualifiedNames(String projectName, List<String> qualifiedNames);

    /**
     * 批量插入包（纯数据库操作，不做存在性检查）
     */
    void insertPackagesBatch(List<CodePackageDO> packages);

    /**
     * 批量更新包（纯数据库操作，不做存在性检查）
     */
    void updatePackagesBatch(List<CodePackageDO> packages);
}
