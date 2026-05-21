package com.poseidon.codegraph.parser.javajdt;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Processor 注册表
 * 
 * 职责：
 * - 管理所有 Processor 的注册
 * - 提供便捷的工厂方法创建常用配置
 * - 领域层纯 Java，不依赖任何外部框架
 */
@Slf4j
public class ProcessorRegistry {
    
    private final List<ASTNodeProcessor> processors = new ArrayList<>();
    
    /**
     * 注册 Processor
     */
    public ProcessorRegistry register(ASTNodeProcessor processor) {
        processors.add(processor);
        log.debug("注册 Processor: {}, 优先级: {}", processor.getName(), processor.getPriority());
        return this;  // 支持链式调用
    }
    
    /**
     * 获取所有 Processor
     */
    public List<ASTNodeProcessor> getAll() {
        return new ArrayList<>(processors);
    }
    
    /**
     * 创建只包含核心解析的注册表
     * 核心解析包括：Package、Unit、Function 节点提取 + 结构关系 + 调用关系
     */
    public static ProcessorRegistry createCoreOnly() {
        ProcessorRegistry registry = new ProcessorRegistry();
        // 节点提取阶段
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.PackageProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.UnitProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.FunctionProcessor());
        // 关系构建阶段
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.CallRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.InheritanceRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.ImplementationRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.OverrideRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.StructureRelationshipProcessor());
        log.info("创建 CoreOnly 注册表（8 个 Processor）");
        return registry;
    }
    
    /**
     * 创建包含核心+端点解析的注册表
     * 包括：核心解析（Package、Unit、Function、关系）+ 端点解析（HTTP、Kafka 等）
     */
    public static ProcessorRegistry createWithEndpoint() {
        return createWithEndpoint(null);
    }

    public static ProcessorRegistry createWithEndpoint(
            com.poseidon.codegraph.parser.javajdt.endpoint.EndpointParsingService endpointParsingService) {
        ProcessorRegistry registry = new ProcessorRegistry();
        // 节点提取阶段
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.PackageProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.UnitProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.FunctionProcessor());
        if (endpointParsingService != null) {
            registry.register(new com.poseidon.codegraph.parser.javajdt.processor.EndpointProcessor(endpointParsingService));
        } else {
            registry.register(new com.poseidon.codegraph.parser.javajdt.processor.EndpointProcessor());
        }
        // 关系构建阶段
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.CallRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.InheritanceRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.ImplementationRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.OverrideRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.parser.javajdt.processor.StructureRelationshipProcessor());
        log.info("创建 WithEndpoint 注册表（9 个 Processor）");
        return registry;
    }
    
    /**
     * 创建空注册表（用于自定义配置）
     */
    public static ProcessorRegistry createEmpty() {
        return new ProcessorRegistry();
    }
}
