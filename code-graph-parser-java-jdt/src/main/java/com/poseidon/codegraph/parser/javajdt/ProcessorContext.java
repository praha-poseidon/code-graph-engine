package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.parser.javajdt.endpoint.EndpointParsingService;
import lombok.Data;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor 上下文
 * 
 * 职责：
 * - 在主 AST 遍历流程中传递，所有 Processor 共享
 * - 提供对 CodeGraph 的访问（所有 Processor 写入同一个 graph）
 * - 提供对 AST 的访问（CompilationUnit）
 * - 提供当前遍历位置的上下文信息（当前类、当前方法）
 * - 提供项目元信息（文件路径、Git 信息等）
 */
@Data
public class ProcessorContext {
    
    // ===== 核心数据 =====
    
    /**
     * 代码图谱（所有 Processor 的输出汇总到这里）
     */
    private final CodeGraph graph = new CodeGraph();
    
    /**
     * JDT 编译单元（AST 根节点）
     */
    private CompilationUnit compilationUnit;
    
    // ===== 项目信息 =====
    
    /**
     * 绝对文件路径
     */
    private String absoluteFilePath;
    
    /**
     * 项目名称
     */
    private String projectName;
    
    /**
     * 项目相对文件路径
     */
    private String projectFilePath;
    
    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支
     */
    private String gitBranch;
    
    /**
     * 包名
     */
    private String packageName;

    /**
     * 外部值字典，供 static-extract trace 规则解析配置类值。
     */
    private Map<String, Map<String, List<String>>> externalValues;

    /**
     * 外部注入的端点解析服务。
     */
    private EndpointParsingService endpointParsingService;

    /**
     * 当前编译单元内的方法声明索引。
     */
    private final Map<String, MethodDeclaration> methodDeclarationsById = new HashMap<>();
    
    // ===== 遍历上下文（当前位置）=====
    
    /**
     * 当前正在处理的类型声明（方便 Processor 获取所属类信息）
     */
    private AbstractTypeDeclaration currentType;
    
    /**
     * 当前正在处理的方法声明（方便 Processor 获取所属方法信息）
     */
    private MethodDeclaration currentMethod;
    
    /**
     * 获取 CodeGraph
     */
    public CodeGraph getGraph() {
        return graph;
    }

    public void indexMethodDeclaration(MethodDeclaration method) {
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            return;
        }
        methodDeclarationsById.put(buildMethodId(binding), method);
    }

    public MethodDeclaration findMethodDeclaration(IMethodBinding binding) {
        if (binding == null) {
            return null;
        }
        return methodDeclarationsById.get(buildMethodId(binding.getMethodDeclaration()));
    }

    private String buildMethodId(IMethodBinding binding) {
        ITypeBinding declaringClass = binding.getDeclaringClass();
        StringBuilder id = new StringBuilder();
        id.append(declaringClass != null ? typeName(declaringClass) : "");
        id.append(".");
        id.append(binding.getName());
        id.append("(");
        ITypeBinding[] params = binding.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                id.append(",");
            }
            id.append(typeName(params[i]));
        }
        id.append(")");
        return id.toString();
    }

    private String typeName(ITypeBinding binding) {
        if (binding == null) {
            return "unknown";
        }
        if (binding.isArray()) {
            return typeName(binding.getElementType()) + "[]";
        }
        if (binding.isPrimitive()) {
            return binding.getName();
        }
        ITypeBinding erasure = binding.getErasure();
        String qualifiedName = erasure != null ? erasure.getQualifiedName() : binding.getQualifiedName();
        return qualifiedName != null && !qualifiedName.isBlank() ? qualifiedName : binding.getName();
    }
}
