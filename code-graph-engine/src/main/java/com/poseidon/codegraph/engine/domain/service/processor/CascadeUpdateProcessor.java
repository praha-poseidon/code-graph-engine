package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

/**
 * 级联更新处理器
 */
@Slf4j
public class CascadeUpdateProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.CASCADE_UPDATE;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String projectFilePath = context.getOldProjectFilePath();
        String absoluteFilePath = context.getAbsoluteFilePath();
        
        log.info("处理级联变更: {}", projectFilePath);
        
        // 级联更新需要读取文件，如果 absoluteFilePath 为空，尝试使用 projectFilePath
        // 这假设运行目录即为项目根目录，或者是相对路径能被正确解析
        if (absoluteFilePath == null) {
            log.warn("级联更新缺少绝对路径，尝试使用项目相对路径: {}", projectFilePath);
            absoluteFilePath = projectFilePath;
        }
        
        // 步骤 1：删除该文件的出边
        context.getWriter().getDeleteFileOutgoingCalls().accept(projectFilePath);
        log.debug("删除出边完成");
        
        // 步骤 2：重建调用关系
        // 注意：rebuildFileCallRelationships 会调用 parseFile，如果 absoluteFilePath 无效会抛出异常
        try {
            int count = rebuildFileCallRelationships(context, absoluteFilePath, projectFilePath, null);
            log.debug("重建调用关系: {} 条", count);
        } catch (Exception e) {
            log.error("级联更新失败: file={}, error={}", projectFilePath, e.getMessage(), e);
            // 级联更新失败不应阻断主流程，记录错误即可
        }
    }
}