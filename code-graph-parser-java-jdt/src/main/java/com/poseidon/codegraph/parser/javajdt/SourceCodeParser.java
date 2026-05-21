package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.model.CodeUnit;

import java.util.List;

/**
 * 源码解析器接口
 * 
 * 职责：
 * - 解析源文件，返回完整的代码图谱（主方法）
 * - 支持细粒度解析（用于测试、增量更新等场景）
 * 
 * 设计理念：
 * - 解析器负责 AST 创建和流程协调
 * - 具体的节点提取由各个 Processor 完成
 * - 通过 ProcessorRegistry 管理和扩展功能
 */
public interface SourceCodeParser {
    
    /**
     * 解析源文件，返回完整的代码图谱（主方法）
     * 
     * 使用 Processor 架构，一次遍历 AST 提取所有信息
     * 
     * @param absoluteFilePath 文件绝对路径（用于读取）
     * @param projectName 项目名称（用于生成唯一 ID）
     * @param projectFilePath 项目相对路径（用于节点属性）
     * @param gitRepoUrl Git 仓库 URL
     * @param gitBranch Git 分支名
     * @return 解析出的代码图谱（包含 Package、Unit、Function、Relationship、Endpoint 等）
     */
    CodeGraph parse(String absoluteFilePath, String projectName, String projectFilePath, 
                    String gitRepoUrl, String gitBranch);

    /**
     * 仅解析包（package 声明）
     * 
     * 用于测试或特定场景，不使用 Processor 架构
     * 
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 包列表（通常一个文件只有一个包）
     */
    List<CodePackage> parsePackages(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析单元（类、接口、枚举等定义）
     * 
     * 用于测试或特定场景，不使用 Processor 架构
     * 
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 单元列表
     */
    List<CodeUnit> parseUnits(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析函数（方法定义）
     * 
     * 用于测试或特定场景，不使用 Processor 架构
     * 
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 函数列表
     */
    List<CodeFunction> parseFunctions(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析关系（包括调用关系、结构关系等）
     * 
     * 用于测试或特定场景，不使用 Processor 架构
     * 
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 关系列表
     */
    List<CodeRelationship> parseRelationships(String absoluteFilePath, String projectName, String projectFilePath);
}