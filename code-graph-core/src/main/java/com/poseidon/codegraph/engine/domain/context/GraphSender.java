package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.model.event.CodeChangeEvent;
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
}

