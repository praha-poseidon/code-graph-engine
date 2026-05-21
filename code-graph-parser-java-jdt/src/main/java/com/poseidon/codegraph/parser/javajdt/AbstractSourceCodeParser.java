package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.parser.javajdt.filter.FilterPipeline;
import com.poseidon.codegraph.parser.javajdt.filter.RelationshipFilter;
import org.eclipse.jdt.core.dom.IMethodBinding;

/**
 * 抽象源码解析器
 * 提供通用的解析流程控制和过滤器支持。
 */
public abstract class AbstractSourceCodeParser implements SourceCodeParser {

    protected final RelationshipFilter filterPipeline;

    protected AbstractSourceCodeParser() {
        this.filterPipeline = new FilterPipeline();
    }

    protected AbstractSourceCodeParser(RelationshipFilter filterPipeline) {
        this.filterPipeline = filterPipeline != null ? filterPipeline : new FilterPipeline();
    }

    /**
     * 判断是否保留该关系
     * 子类在解析关系时调用此方法进行过滤
     */
    protected boolean shouldKeepRelationship(CodeRelationship relationship, IMethodBinding targetBinding) {
        return filterPipeline.shouldKeep(relationship, targetBinding);
    }
}
