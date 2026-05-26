package com.poseidon.codegraph.engine.domain.service.merge;

import com.poseidon.codegraph.model.CodeFunction;

import java.util.List;

/**
 * 函数节点保存计划。
 */
public record CodeFunctionSavePlan(
    List<CodeFunction> toInsert,
    List<CodeFunction> toUpdate,
    List<CodeFunction> skipped
) {
}
