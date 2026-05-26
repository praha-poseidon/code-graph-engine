package com.poseidon.codegraph.engine.domain.service;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.model.event.ChangeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGraphServiceTest {

    @Test
    void rejectsContextWhenNoProcessorSupportsChangeType() {
        CodeGraphService service = new CodeGraphService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        context.setProjectFilePath("src/main/java/demo/User.java");
        context.setChangeType(null);

        assertThatThrownBy(() -> service.handle(context))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No processor found");
    }

    @Test
    void wrapsProcessorFailureWithProcessorName() {
        CodeGraphService service = new CodeGraphService();
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName("demo");
        context.setChangeType(ChangeType.SOURCE_DELETED);
        context.setOldProjectFilePath("src/main/java/demo/User.java");

        assertThatThrownBy(() -> service.handle(context))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("RemovedSourceProcessor");
    }
}
