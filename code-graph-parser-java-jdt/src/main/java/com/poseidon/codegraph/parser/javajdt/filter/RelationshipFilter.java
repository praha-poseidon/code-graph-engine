package com.poseidon.codegraph.parser.javajdt.filter;

import com.poseidon.codegraph.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;

/**
 * 关系过滤器接口
 * 用于在解析阶段过滤不需要的调用关系
 */
public interface RelationshipFilter {
    
    /**
     * 是否保留该关系
     * 
     * @param relationship 已构建的关系对象
     * @param targetBinding 目标方法的 JDT 绑定信息（包含类名、包名、修饰符等丰富语义）
     * @return true 保留，false 丢弃
     */
    boolean shouldKeep(CodeRelationship relationship, IMethodBinding targetBinding);
}

