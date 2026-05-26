package com.poseidon.codegraph.engine.domain.service.merge;

import com.poseidon.codegraph.model.CodeFunction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeFunctionSavePlannerTest {

    private final CodeFunctionSavePlanner planner = new CodeFunctionSavePlanner();

    @Test
    void skipsPlaceholderWhenRealFunctionAlreadyExists() {
        CodeFunction incoming = function("com.example.UserService.get()", true);
        CodeFunction existing = function("com.example.UserService.get()", false);

        CodeFunctionSavePlan plan = planner.plan(List.of(incoming), Map.of(existing.getId(), existing));

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.toUpdate()).isEmpty();
        assertThat(plan.skipped()).containsExactly(incoming);
    }

    @Test
    void updatesPlaceholderWhenRealFunctionArrives() {
        CodeFunction incoming = function("com.example.UserService.get()", false);
        CodeFunction existing = function("com.example.UserService.get()", true);

        CodeFunctionSavePlan plan = planner.plan(List.of(incoming), Map.of(existing.getId(), existing));

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.toUpdate()).containsExactly(incoming);
        assertThat(plan.skipped()).isEmpty();
    }

    @Test
    void insertsMissingFunction() {
        CodeFunction incoming = function("com.example.UserService.get()", true);

        CodeFunctionSavePlan plan = planner.plan(List.of(incoming), Map.of());

        assertThat(plan.toInsert()).containsExactly(incoming);
        assertThat(plan.toUpdate()).isEmpty();
        assertThat(plan.skipped()).isEmpty();
    }

    @Test
    void skipsPlaceholderWhenPlaceholderAlreadyExists() {
        CodeFunction incoming = function("com.example.UserService.get()", true);
        CodeFunction existing = function("com.example.UserService.get()", true);

        CodeFunctionSavePlan plan = planner.plan(List.of(incoming), Map.of(existing.getId(), existing));

        assertThat(plan.toInsert()).isEmpty();
        assertThat(plan.toUpdate()).isEmpty();
        assertThat(plan.skipped()).containsExactly(incoming);
    }

    private CodeFunction function(String id, boolean placeholder) {
        CodeFunction function = new CodeFunction();
        function.setId(id);
        function.setQualifiedName(id);
        function.setIsPlaceholder(placeholder);
        return function;
    }
}
