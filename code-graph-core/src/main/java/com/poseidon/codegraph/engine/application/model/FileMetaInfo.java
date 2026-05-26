package com.poseidon.codegraph.engine.application.model;

import lombok.Data;

/**
 * 文件元信息
 * 用于级联变更时查询依赖文件的 Git 信息
 */
@Data
public class FileMetaInfo {
    /**
     * 项目文件路径
     */
    private String projectFilePath;
    
    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支名
     */
    private String gitBranch;
}

