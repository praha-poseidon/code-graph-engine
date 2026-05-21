package com.poseidon.codegraph.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码函数
 * 表示方法/函数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeFunction extends CodeNode {
    /**
     * 方法签名
     * 例如：saveUser(User):void
     */
    private String signature;
    
    /**
     * 返回类型
     */
    private String returnType;
    
    /**
     * 修饰符：public, private, static, final 等
     */
    private List<String> modifiers = new ArrayList<>();
    
    /**
     * 是否静态方法
     */
    private Boolean isStatic;
    
    /**
     * 是否异步方法
     */
    private Boolean isAsync;
    
    /**
     * 是否构造器
     */
    private Boolean isConstructor;
    
    /**
     * 是否为占位符节点
     * 当调用关系的目标节点不存在时，创建占位符节点
     */
    private Boolean isPlaceholder;
}

