package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodeFunctionDO;

import java.util.List;
import java.util.Set;

/**
 * 代码函数仓储接口
 */
public interface CodeFunctionRepository {

    /**
     * 根据文件路径查找所有函数
     */
    List<CodeFunctionDO> findFunctionsByProjectFilePath(String projectName, String projectFilePath);

    /**
     * 批量查询函数是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    Set<String> findExistingFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames);

    /**
     * 根据全限定名批量查询函数详情
     * @param qualifiedNames 全限定名列表
     * @return 函数详情列表
     */
    List<CodeFunctionDO> findFunctionsByQualifiedNames(String projectName, List<String> qualifiedNames);

    /**
     * 批量插入函数（纯数据库操作，不做存在性检查）
     */
    void insertFunctionsBatch(List<CodeFunctionDO> functions);

    /**
     * 批量更新函数（纯数据库操作，不做存在性检查）
     */
    void updateFunctionsBatch(List<CodeFunctionDO> functions);
    
    /**
     * 根据 ID 删除函数
     * @param id 函数 ID
     */
    void deleteById(String projectName, String id);
}
