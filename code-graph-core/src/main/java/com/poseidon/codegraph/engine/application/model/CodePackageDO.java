package com.poseidon.codegraph.engine.application.model;

import lombok.Data;

/**
 * 代码包数据对象（应用层）
 */
@Data
public class CodePackageDO {
    private String id;
    private String name;
    private String qualifiedName;
    private String language;
    private String projectName;
    private String projectFilePath;
    private String gitRepoUrl;
    private String gitBranch;
    private String packagePath;
}
