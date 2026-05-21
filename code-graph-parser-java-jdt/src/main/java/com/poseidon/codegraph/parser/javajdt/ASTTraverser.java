package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeGraph;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一 AST 遍历器（主流程）
 * 
 * 职责：
 * - 遍历 AST（只遍历一次）
 * - 在遍历过程中触发所有 Processor 的回调
 * - 保证所有 Processor 都能访问到 AST 节点
 * 
 * 设计特点：
 * - 单一职责：只负责遍历和分发，不负责具体的提取逻辑
 * - 开闭原则：对扩展开放（添加新 Processor），对修改关闭（Traverser 不需要改）
 * - 异常隔离：某个 Processor 出错不影响其他 Processor
 */
@Slf4j
public class ASTTraverser {
    
    private final List<ASTNodeProcessor> processors;
    
    /**
     * 构造函数
     * @param processors Processor 列表（会按优先级自动排序）
     */
    public ASTTraverser(List<ASTNodeProcessor> processors) {
        this.processors = processors.stream()
            .filter(ASTNodeProcessor::isEnabled)
            .sorted(Comparator.comparingInt(ASTNodeProcessor::getPriority))
            .collect(Collectors.toList());
        
        log.info("ASTTraverser 初始化完成，已启用 {} 个 Processor: {}",
            this.processors.size(),
            this.processors.stream().map(ASTNodeProcessor::getName).collect(Collectors.joining(", "))
        );
    }
    
    /**
     * 遍历 AST 并触发所有 Processor
     * 
     * @param context Processor 上下文
     * @return 代码图谱（包含所有 Processor 的处理结果）
     */
    public CodeGraph traverse(ProcessorContext context) {
        CompilationUnit cu = context.getCompilationUnit();
        
        log.debug("开始 AST 遍历: file={}", context.getProjectFilePath());
        
        // 1. 处理 Package
        PackageDeclaration pkg = cu.getPackage();
        if (pkg != null) {
            String packageName = pkg.getName().getFullyQualifiedName();
            context.setPackageName(packageName);
            triggerOnPackage(pkg, context);
        }
        
        // 2. 遍历所有类型声明（Class/Interface/Enum/Annotation）
        for (Object type : cu.types()) {
            if (type instanceof AbstractTypeDeclaration) {
                AbstractTypeDeclaration typeDecl = (AbstractTypeDeclaration) type;
                context.setCurrentType(typeDecl);
                
                triggerOnTypeDeclaration(typeDecl, context);
                
                // 3. 遍历类型中的所有方法
                for (Object bodyDeclaration : typeDecl.bodyDeclarations()) {
                    if (!(bodyDeclaration instanceof MethodDeclaration)) {
                        continue;
                    }
                    MethodDeclaration method = (MethodDeclaration) bodyDeclaration;
                    context.setCurrentMethod(method);
                    context.indexMethodDeclaration(method);
                    
                    triggerOnMethodDeclaration(method, typeDecl, context);
                    
                    // 4. 遍历方法体中的所有方法调用
                    if (method.getBody() != null) {
                        method.getBody().accept(new ASTVisitor() {
                            @Override
                            public boolean visit(MethodInvocation invocation) {
                                triggerOnMethodInvocation(invocation, method, typeDecl, context);
                                return true;  // 继续遍历子节点
                            }
                        });
                    }
                }
            }
        }
        
        log.info("AST 遍历完成: file={}, packages={}, units={}, functions={}, relationships={}, endpoints={}",
            context.getProjectFilePath(),
            context.getGraph().getPackagesAsList().size(),
            context.getGraph().getUnitsAsList().size(),
            context.getGraph().getFunctionsAsList().size(),
            context.getGraph().getRelationshipsAsList().size(),
            context.getGraph().getEndpointsAsList().size()
        );
        
        // 触发遍历完成回调（用于构建关系等后处理）
        triggerOnTraversalComplete(context);
        
        return context.getGraph();
    }
    
    /**
     * 触发所有 Processor 的 onPackage 回调
     */
    private void triggerOnPackage(PackageDeclaration pkg, ProcessorContext context) {
        for (ASTNodeProcessor processor : processors) {
            try {
                processor.onPackage(pkg, context);
            } catch (Exception e) {
                log.error("Processor [{}] 处理 Package 失败: {}", 
                    processor.getName(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 触发所有 Processor 的 onTypeDeclaration 回调
     */
    private void triggerOnTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        for (ASTNodeProcessor processor : processors) {
            try {
                processor.onTypeDeclaration(type, context);
            } catch (Exception e) {
                log.error("Processor [{}] 处理 TypeDeclaration 失败: type={}, error={}", 
                    processor.getName(), type.getName().getIdentifier(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 触发所有 Processor 的 onMethodDeclaration 回调
     */
    private void triggerOnMethodDeclaration(
        MethodDeclaration method,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        for (ASTNodeProcessor processor : processors) {
            try {
                processor.onMethodDeclaration(method, enclosingType, context);
            } catch (Exception e) {
                log.error("Processor [{}] 处理 MethodDeclaration 失败: method={}, error={}", 
                    processor.getName(), method.getName().getIdentifier(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 触发所有 Processor 的 onMethodInvocation 回调
     */
    private void triggerOnMethodInvocation(
        MethodInvocation invocation,
        MethodDeclaration enclosingMethod,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        for (ASTNodeProcessor processor : processors) {
            try {
                processor.onMethodInvocation(invocation, enclosingMethod, enclosingType, context);
            } catch (Exception e) {
                log.error("Processor [{}] 处理 MethodInvocation 失败: invocation={}, error={}", 
                    processor.getName(), invocation.getName().getIdentifier(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * 触发所有 Processor 的 onTraversalComplete 回调
     * 在 AST 遍历完成后执行，适用于需要基于所有节点来构建关系的场景
     */
    private void triggerOnTraversalComplete(ProcessorContext context) {
        log.debug("开始触发 onTraversalComplete 回调");
        
        for (ASTNodeProcessor processor : processors) {
            try {
                processor.onTraversalComplete(context);
            } catch (Exception e) {
                log.error("Processor [{}] 处理 TraversalComplete 失败: {}", 
                    processor.getName(), e.getMessage(), e);
            }
        }
        
        log.debug("onTraversalComplete 回调完成");
    }
}
