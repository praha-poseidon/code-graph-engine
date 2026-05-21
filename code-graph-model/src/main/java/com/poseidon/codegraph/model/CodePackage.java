package com.poseidon.codegraph.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码包
 * 表示包/模块/命名空间
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodePackage extends CodeNode {
    /**
     * 包路径（用 / 分隔）
     * 例如：com/example/service
     */
    private String packagePath;
}

