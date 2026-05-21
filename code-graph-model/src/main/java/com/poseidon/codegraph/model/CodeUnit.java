package com.poseidon.codegraph.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码单元
 * 表示类/接口/结构体/枚举等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeUnit extends CodeNode {
    /**
     * 单元类型：class, interface, enum, annotation, record
     */
    private String unitType;
    
    /**
     * 修饰符：public, private, abstract, static, final 等
     */
    private List<String> modifiers = new ArrayList<>();
    
    /**
     * 是否抽象
     */
    private Boolean isAbstract;
    
    /**
     * 所属包 ID
     */
    private String packageId;
    
    /**
     * 包含的函数列表
     */
    private List<CodeFunction> functions = new ArrayList<>();
    
    /**
     * 添加函数
     */
    public void addFunction(CodeFunction function) {
        if (this.functions == null) {
            this.functions = new ArrayList<>();
        }
        this.functions.add(function);
    }
}

