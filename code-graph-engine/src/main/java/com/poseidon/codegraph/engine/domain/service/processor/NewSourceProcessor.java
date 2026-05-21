package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

/**
 * 源码新增处理器
 */
@Slf4j
public class NewSourceProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.SOURCE_ADDED;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String projectFilePath = context.getNewProjectFilePath();
        String absoluteFilePath = context.getAbsoluteFilePath();
        
        log.info("处理新增文件: {}", projectFilePath);
        
        // 步骤 1：解析文件
        GraphDelta delta = parseGraphDelta(context, absoluteFilePath, projectFilePath);
        log.debug("解析完成: {} 个类, {} 个方法",
            delta.units() == null ? 0 : delta.units().size(),
            delta.functions() == null ? 0 : delta.functions().size());
        
        // 步骤 2：保存节点
        saveNodes(delta, context);
        log.debug("保存节点完成");
        
        // 步骤 3：建立调用关系
        int relationshipCount = rebuildFileCallRelationships(context, absoluteFilePath, projectFilePath, delta);
        log.debug("建立调用关系: {} 条", relationshipCount);
    }
}
