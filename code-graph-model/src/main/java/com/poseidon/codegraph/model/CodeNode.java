package com.poseidon.codegraph.model;

import lombok.Data;

/**
 * 代码节点基类
 * 所有代码元素的抽象基类
 */
@Data
public abstract class CodeNode {
    /**
     * 唯一标识
     */
    private String id;
    
    /**
     * 名称
     */
    private String name;
    
    /**
     * 全限定名
     */
    private String qualifiedName;
    
    /**
     * 语言
     */
    private String language;

    /**
     * 所属项目名称。
     */
    private String projectName;
    
    /**
     * 项目文件路径(某个项目的绝对路径)
     * 比如你的项目code-graph存储到了git上，那这个项目下的所有节点的路径都是从code-graph开始的
     * 一句话节点的文件位置就是从项目所在的文件夹开始的
     */
    private String projectFilePath;
    
    /**
     * Git 仓库 URL
     * 例如：https://github.com/company/project.git
     * 用于级联变更时从远程拉取代码
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支名
     * 例如：main, develop, feature/xxx
     */
    private String gitBranch;
    
    /**
     * 起始行号
     */
    private Integer startLine;
    
    /**
     * 结束行号
     */
    private Integer endLine;

    public String getProjectFilePath() {
        return projectFilePath;
    }

    public void setProjectFilePath(String projectFilePath) {
        this.projectFilePath = projectFilePath;
    }
}
