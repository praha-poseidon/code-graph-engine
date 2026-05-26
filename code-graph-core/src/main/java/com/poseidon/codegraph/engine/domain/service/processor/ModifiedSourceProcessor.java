package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.delta.GraphDelta;
import com.poseidon.codegraph.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 源码修改处理器
 */
@Slf4j
public class ModifiedSourceProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.SOURCE_MODIFIED;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String oldProjectFilePath = context.getOldProjectFilePath();
        String newProjectFilePath = context.getNewProjectFilePath();
        String absoluteFilePath = context.getAbsoluteFilePath();
        
        log.info("处理修改文件: {}", newProjectFilePath);
        
        // 步骤 1：查找谁依赖我（入边）
        List<String> dependentFiles = new ArrayList<>(
            context.getReader().getFindWhoCallsMe().apply(oldProjectFilePath)
        );
        
        // 排除自己，避免不必要的自我级联更新（自身更新由当前 Processor 处理）
        dependentFiles.remove(oldProjectFilePath);
        
        log.debug("找到依赖文件: {} 个", dependentFiles.size());
        
        // 步骤 2：触发级联变更（依赖我的文件）
        triggerCascadeChanges(context, dependentFiles);
        
        // 步骤 3：删除该文件的旧节点
        List<CodeUnit> oldUnits = context.getReader().getFindUnitsByProjectFilePath().apply(oldProjectFilePath);
        List<CodeFunction> oldFunctions = context.getReader().getFindFunctionsByProjectFilePath().apply(oldProjectFilePath);
        List<com.poseidon.codegraph.model.CodeEndpoint> oldEndpoints = 
            context.getReader().getFindEndpointsByProjectFilePath().apply(oldProjectFilePath);
        
        log.info("准备删除旧节点: {} 个单元, {} 个方法, {} 个端点", oldUnits.size(), oldFunctions.size(), oldEndpoints.size());
        deleteNodes(oldUnits, oldFunctions, oldEndpoints, context);
        log.info("旧节点删除完成");
        
        // 步骤 4：解析新文件
        // 注意：这里我们使用 absoluteFilePath 来读取文件内容，使用 newProjectFilePath 作为节点标识
        GraphDelta newDelta = parseGraphDelta(context, absoluteFilePath, newProjectFilePath);
        log.debug("解析新文件: {} 个类, {} 个方法",
            newDelta.units() == null ? 0 : newDelta.units().size(),
            newDelta.functions() == null ? 0 : newDelta.functions().size());
        
        // 步骤 5：保存新节点
        saveNodes(newDelta, context);
        log.debug("保存新节点完成");
        
        // 步骤 6：重建当前文件的调用关系
        int relationshipCount = rebuildFileCallRelationships(context, absoluteFilePath, newProjectFilePath, newDelta);
        log.debug("重建当前文件调用关系: {} 条", relationshipCount);
    }
}
