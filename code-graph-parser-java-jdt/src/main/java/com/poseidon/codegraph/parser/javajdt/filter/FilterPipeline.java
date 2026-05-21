package com.poseidon.codegraph.parser.javajdt.filter;

import com.poseidon.codegraph.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 关系过滤器管道
 * 责任链模式实现，管理多个过滤器并依次执行
 */
public class FilterPipeline implements RelationshipFilter {
    
    private final List<RelationshipFilter> filters = new ArrayList<>();
    
    public FilterPipeline addFilter(RelationshipFilter filter) {
        if (filter != null) {
            filters.add(filter);
        }
        return this;
    }
    
    @Override
    public boolean shouldKeep(CodeRelationship relationship, IMethodBinding targetBinding) {
        for (RelationshipFilter filter : filters) {
            if (!filter.shouldKeep(relationship, targetBinding)) {
                return false;
            }
        }
        return true;
    }
}

