package com.poseidon.codegraph.engine.domain.service.merge;

import com.poseidon.codegraph.model.CodeFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规划函数节点保存动作。
 *
 * 领域规则：
 * - 占位符节点不能覆盖真实节点。
 * - 真实节点可以补全占位符节点。
 */
public class CodeFunctionSavePlanner {

    public CodeFunctionSavePlan plan(List<CodeFunction> incomingFunctions, Map<String, CodeFunction> existingFunctions) {
        List<CodeFunction> toInsert = new ArrayList<>();
        List<CodeFunction> toUpdate = new ArrayList<>();
        List<CodeFunction> skipped = new ArrayList<>();

        if (incomingFunctions == null || incomingFunctions.isEmpty()) {
            return new CodeFunctionSavePlan(toInsert, toUpdate, skipped);
        }

        Map<String, CodeFunction> existing = existingFunctions != null ? existingFunctions : Map.of();
        for (CodeFunction incoming : incomingFunctions) {
            CodeFunction current = existing.get(incoming.getId());
            if (current == null) {
                toInsert.add(incoming);
                continue;
            }

            if (isPlaceholder(incoming) && !isPlaceholder(current)) {
                skipped.add(incoming);
                continue;
            }

            if (isPlaceholder(incoming) && isPlaceholder(current)) {
                skipped.add(incoming);
                continue;
            }

            toUpdate.add(incoming);
        }

        return new CodeFunctionSavePlan(toInsert, toUpdate, skipped);
    }

    private boolean isPlaceholder(CodeFunction function) {
        return Boolean.TRUE.equals(function.getIsPlaceholder());
    }
}
