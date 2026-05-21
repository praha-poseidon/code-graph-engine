package com.poseidon.codegraph.model.event;

/**
 * 变更类型
 */
public enum ChangeType {
    /**
     * 源码新增
     * 对应：NewSourceProcessor
     */
    SOURCE_ADDED,
    
    /**
     * 源码删除
     * 对应：RemovedSourceProcessor
     */
    SOURCE_DELETED,
    
    /**
     * 源码修改
     * 对应：ModifiedSourceProcessor
     */
    SOURCE_MODIFIED,
    
    /**
     * 级联更新（依赖变更引起的更新）
     * 对应：CascadeUpdateProcessor
     */
    CASCADE_UPDATE
}
