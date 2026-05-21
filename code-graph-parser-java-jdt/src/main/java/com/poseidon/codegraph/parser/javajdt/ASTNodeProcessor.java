package com.poseidon.codegraph.parser.javajdt;

import org.eclipse.jdt.core.dom.*;

/**
 * AST 节点处理器接口
 * 
 * 设计理念：
 * - 在统一的 AST 遍历主流程中被回调
 * - 每个实现关注自己感兴趣的节点类型
 * - 多个 Processor 可以处理同一个节点（互不干扰）
 * 
 * 使用示例：
 * - CoreStructureProcessor：提取包、类、方法、调用关系
 * - EndpointProcessor：提取 HTTP/Kafka 端点
 * - SecurityProcessor：提取安全注解
 * - MetricsProcessor：提取性能指标
 */
public interface ASTNodeProcessor {
    
    /**
     * 当遍历到 Package 节点时回调
     * 
     * @param pkg 包声明节点
     * @param context 处理器上下文（包含 CodeGraph、CompilationUnit 等）
     */
    default void onPackage(PackageDeclaration pkg, ProcessorContext context) {
        // 默认不处理
    }
    
    /**
     * 当遍历到 Class/Interface/Enum 节点时回调
     * 
     * @param type 类型声明节点
     * @param context 处理器上下文
     */
    default void onTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        // 默认不处理
    }
    
    /**
     * 当遍历到 Method 节点时回调
     * 
     * @param method 方法声明节点
     * @param enclosingType 所属的类
     * @param context 处理器上下文
     */
    default void onMethodDeclaration(
        MethodDeclaration method,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        // 默认不处理
    }
    
    /**
     * 当遍历到 MethodInvocation 节点时回调
     * 
     * @param invocation 方法调用节点
     * @param enclosingMethod 所在的方法
     * @param enclosingType 所在的类
     * @param context 处理器上下文
     */
    default void onMethodInvocation(
        MethodInvocation invocation,
        MethodDeclaration enclosingMethod,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        // 默认不处理
    }
    
    /**
     * 当 AST 遍历完成后回调（生命周期钩子）
     * 
     * 适用场景：
     * - 需要基于已提取的所有节点来构建关系
     * - 需要进行全局的后处理或验证
     * 
     * 执行时机：在所有节点遍历回调（onPackage、onTypeDeclaration 等）执行完毕后
     * 
     * @param context 处理器上下文（此时 context.getGraph() 已包含所有提取的节点）
     */
    default void onTraversalComplete(ProcessorContext context) {
        // 默认不处理
    }
    
    /**
     * 处理器名称（用于日志和调试）
     */
    String getName();
    
    /**
     * 处理器优先级（数字越小越先执行）
     * 
     * 建议：
     * - 核心结构处理器：1-10
     * - 领域特定处理器：100-200
     * - 用户自定义处理器：1000+
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * 是否启用（可用于配置开关）
     */
    default boolean isEnabled() {
        return true;
    }
}
