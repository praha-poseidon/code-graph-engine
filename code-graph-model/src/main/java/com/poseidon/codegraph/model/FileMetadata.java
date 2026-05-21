package com.poseidon.codegraph.model;

import lombok.Data;

/**
 * 文件元数据（领域模型）
 * 用于级联变更时传递文件的 Git 信息
 */
@Data
public class FileMetadata {
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

