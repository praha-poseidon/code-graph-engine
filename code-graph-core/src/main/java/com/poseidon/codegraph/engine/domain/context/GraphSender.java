package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.model.event.CodeChangeEvent;
import com.poseidon.codegraph.model.event.NodeChangeEvent;
import lombok.Data;
import java.util.function.Consumer;

/**
 * 图谱事件发送器
 * 领域层通过此接口发送变更事件（如级联更新）
 */
@Data
public class GraphSender {

    /**
     * 发送代码变更事件
     */
    private Consumer<CodeChangeEvent> sendEvent;

    /**
     * 发送节点级变更事件（增/删/改）
     */
    private Consumer<NodeChangeEvent> sendNodeEvent;
}
