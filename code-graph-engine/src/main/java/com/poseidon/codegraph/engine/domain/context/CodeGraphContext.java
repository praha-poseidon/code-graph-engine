package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.model.event.ChangeType;
import com.poseidon.codegraph.spi.CodeGraphParserRegistry;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 代码图谱上下文（领域层交互核心对象）
 * 包含：
 * 1. 变更数据（参数）
 * 2. 操作能力（Reader/Writer）
 * 3. 事件能力（Sender）
 */
@Data
public class CodeGraphContext {
    
    // ========== 变更参数 ==========
    
    /**
     * 项目名称
     */
    private String projectName;
    
    /**
     * 文件绝对路径（用于读取内容）
     */
    private String absoluteFilePath;

    public String getAbsoluteFilePath() {
        // 领域逻辑：如果绝对路径为空，尝试使用当前工作目录 + 项目路径推导
        if (this.absoluteFilePath == null || this.absoluteFilePath.trim().isEmpty()) {
            if (this.projectFilePath != null && !this.projectFilePath.trim().isEmpty()) {
                try {
                    return java.nio.file.Path.of(System.getProperty("user.home"), this.projectFilePath)
                        .toAbsolutePath().toString();
                } catch (Exception e) {
                    // 忽略异常，返回原始值
                }
            }
        }
        return this.absoluteFilePath;
    }
    
    /**
     * 项目文件路径（用于标识，相对于 Git 根）
     */
    private String projectFilePath;
    
    /**
     * 包名（从源码解析得到，用于端点解析等）
     */
    private String packageName;
    
    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支名
     */
    private String gitBranch;
    
    /**
     * classpath 条目
     */
    private String[] classpathEntries;
    
    /**
     * sourcepath 条目
     */
    private String[] sourcepathEntries;

    /**
     * 源码语言，用来选择对应的解析器 SPI。
     */
    private String language = "java";

    /**
     * 外部值字典。
     *
     * <p>例如 Spring application.yml/properties 会被扫描成 namespace=config 的字典，
     * static-extract trace 规则可以通过 namespace + lookup 读取这些值。
     */
    private Map<String, Map<String, List<String>>> externalValues;

    /**
     * 外部传入的 endpoint SER 规则文本。
     */
    private List<String> endpointRuleSources;

    /**
     * 外部传入的 trace SER 规则文本。
     */
    private List<String> traceRuleSources;

    /**
     * 解析器注册表。
     */
    private CodeGraphParserRegistry parserRegistry;
    
    /**
     * 旧文件项目路径
     */
    private String oldProjectFilePath;
    
    /**
     * 新文件项目路径
     */
    private String newProjectFilePath;
    
    /**
     * 变更类型
     */
    private ChangeType changeType;
    
    // ========== 操作能力 ==========
    
    /**
     * 图谱读取器
     */
    private GraphReader reader;
    
    /**
     * 图谱写入器
     */
    private GraphWriter writer;
    
    /**
     * 图谱事件发送器
     */
    private GraphSender sender;
    
    public CodeGraphContext() {
        this.reader = new GraphReader();
        this.writer = new GraphWriter();
        this.sender = new GraphSender();
    }
}
