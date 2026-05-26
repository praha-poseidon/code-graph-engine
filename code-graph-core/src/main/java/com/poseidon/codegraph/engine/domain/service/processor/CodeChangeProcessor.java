package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;

/**
 * 代码变更处理器策略接口
 */
public interface CodeChangeProcessor {
    
    /**
     * 是否支持当前上下文
     */
    boolean support(CodeGraphContext context);
    
    /**
     * 执行处理
     */
    void handle(CodeGraphContext context);
}

