package com.poseidon.codegraph.engine.application.model;

import lombok.Data;
import java.util.List;

/**
 * 代码单元数据对象（应用层）
 */
@Data
public class CodeUnitDO {
    private String id;
    private String name;
    private String qualifiedName;
    private String language;
    private String projectName;
    private String projectFilePath;
    private String gitRepoUrl;
    private String gitBranch;
    private Integer startLine;
    private Integer endLine;
    private String unitType;
    private List<String> modifiers;
    private Boolean isAbstract;
    private String packageId;
}
