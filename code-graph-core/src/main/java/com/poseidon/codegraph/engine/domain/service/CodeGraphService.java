package com.poseidon.codegraph.engine.domain.service;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.service.processor.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码图谱领域服务
 * 职责：变更处理的分发器
 * 
 * public: 领域服务入口
 */
@Slf4j
public class CodeGraphService {
    
    private final List<CodeChangeProcessor> processors = new ArrayList<>();
    
    public CodeGraphService() {
        // 注册所有处理器
        processors.add(new CascadeUpdateProcessor());
        processors.add(new NewSourceProcessor());
        processors.add(new RemovedSourceProcessor());
        processors.add(new ModifiedSourceProcessor());
    }
    
    /**
     * 处理文件变更
     */
    public void handle(CodeGraphContext context) {
        log.debug("开始处理代码变更: changeType={}, projectFilePath={}", 
                  context.getChangeType(), 
                  context.getNewProjectFilePath() != null ? context.getNewProjectFilePath() : context.getOldProjectFilePath());
        
        for (CodeChangeProcessor processor : processors) {
            if (processor.support(context)) {
                log.debug("找到匹配的处理器: {}", processor.getClass().getSimpleName());
                try {
                    processor.handle(context);
                    log.debug("处理器执行完成: {}", processor.getClass().getSimpleName());
                    return;
                } catch (Exception e) {
                    log.error("处理器执行失败: processor={}, changeType={}, error={}", 
                              processor.getClass().getSimpleName(), 
                              context.getChangeType(), 
                              e.getMessage(), e);
                    throw new RuntimeException("处理器执行失败: " + processor.getClass().getSimpleName(), e);
                }
            }
        }
        
        log.error("未找到支持的处理器: changeType={}, projectFilePath={}", 
                  context.getChangeType(), 
                  context.getNewProjectFilePath() != null ? context.getNewProjectFilePath() : context.getOldProjectFilePath());
        throw new IllegalArgumentException("No processor found for context: changeType=" + context.getChangeType());
    }
}
