package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Function 节点提取器
 * 
 * 职责：
 * - 从 AST 中提取 Function 节点
 * - 将 Function 添加到所属 Unit 的临时列表（用于后续关系构建）
 * - 不创建关系（关系由 StructureRelationshipProcessor 统一创建）
 * 
 * 优先级：3（节点提取阶段，在 Unit 之后）
 */
@Slf4j
public class FunctionProcessor implements ASTNodeProcessor {
    
    @Override
    public void onMethodDeclaration(
        MethodDeclaration method,
        AbstractTypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        // 从 graph 中找到对应的 CodeUnit
        CodeUnit unit = findUnitInGraph(context.getGraph(), enclosingType);
        if (unit == null) {
            log.warn("未找到对应的 CodeUnit，跳过方法: {}", method.getName().getIdentifier());
            return;
        }
        
        CodeFunction function = new CodeFunction();
        function.setName(method.getName().getIdentifier());
        
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.warn("方法绑定解析失败，使用源码信息降级创建 Function: method={}", function.getName());
            String signature = JdtGraphIds.sourceMethodSignature(method);
            String qualifiedName = JdtGraphIds.sourceQualifiedMethodSignature(unit.getQualifiedName(), method);
            function.setQualifiedName(qualifiedName);
            function.setId(JdtGraphIds.functionId(qualifiedName));
            function.setSignature(signature);
            function.setReturnType(method.getReturnType2() != null ? method.getReturnType2().toString() : "void");
        } else {
            // 使用 IMethodBinding 生成 qualifiedName（与 CallRelationshipProcessor 保持一致）
            String qualifiedName = JdtGraphIds.qualifiedMethodSignature(binding);
            function.setQualifiedName(qualifiedName);
            function.setId(JdtGraphIds.functionId(qualifiedName));

            // 生成简单签名（用于显示）
            String signature = buildSimpleSignature(binding);
            function.setSignature(signature);

            ITypeBinding returnTypeBinding = binding.getReturnType();
            if (returnTypeBinding != null) {
                function.setReturnType(getQualifiedTypeName(returnTypeBinding));
            } else {
                function.setReturnType("void");
            }
        }
        
        int modifiers = method.getModifiers();
        function.setModifiers(extractModifiers(modifiers));
        function.setIsStatic(Modifier.isStatic(modifiers));
        function.setIsConstructor(method.isConstructor());
        function.setIsAsync(false);
        function.setIsPlaceholder(false);
        
        function.setProjectFilePath(unit.getProjectFilePath());
        function.setStartLine(context.getCompilationUnit().getLineNumber(method.getStartPosition()));
        function.setEndLine(context.getCompilationUnit().getLineNumber(method.getStartPosition() + method.getLength()));
        function.setLanguage("java");
        function.setGitRepoUrl(context.getGitRepoUrl());
        function.setGitBranch(context.getGitBranch());
        
        // 添加到 Unit 的 functions 列表（用于后续关系构建）
        unit.addFunction(function);
        
        // 添加到 Graph
        context.getGraph().addFunction(function);
        
        log.debug("✓ 提取 Function: {}", function.getName());
    }
    
    @Override
    public int getPriority() {
        return 3;  // 节点提取阶段
    }
    
    @Override
    public String getName() {
        return "FunctionProcessor";
    }
    
    // ========== 辅助方法 ==========
    
    private CodeUnit findUnitInGraph(CodeGraph graph, AbstractTypeDeclaration typeDecl) {
        ITypeBinding binding = typeDecl.resolveBinding();
        if (binding != null) {
            String qualifiedName = binding.getQualifiedName();
            return graph.getUnitsAsList().stream()
                .filter(u -> qualifiedName.equals(u.getQualifiedName()))
                .findFirst()
                .orElse(null);
        }
        String simpleName = typeDecl.getName().getIdentifier();
        return graph.getUnitsAsList().stream()
            .filter(u -> simpleName.equals(u.getName()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 生成简单签名（用于显示）
     * 格式：method(Param1,Param2)
     */
    private String buildSimpleSignature(IMethodBinding binding) {
        StringBuilder signature = new StringBuilder();
        signature.append(binding.getName());
        signature.append("(");
        
        ITypeBinding[] paramTypes = binding.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                signature.append(",");
            }
            // 使用简单类名
            String typeName = getSimpleTypeName(paramTypes[i]);
            signature.append(typeName);
        }
        
        signature.append(")");
        return signature.toString();
    }
    
    /**
     * 获取简单类型名（不带包名）
     */
    private String getSimpleTypeName(ITypeBinding typeBinding) {
        if (typeBinding == null) {
            return "unknown";
        }
        
        if (typeBinding.isArray()) {
            return getSimpleTypeName(typeBinding.getElementType()) + "[]";
        }
        
        if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        }
        
        // 使用简单名称
        String name = typeBinding.getName();
        // 移除泛型信息
        int genericStart = name.indexOf('<');
        if (genericStart > 0) {
            name = name.substring(0, genericStart);
        }
        return name;
    }
    
    private String getQualifiedTypeName(ITypeBinding typeBinding) {
        if (typeBinding == null) {
            return "unknown";
        }
        
        if (typeBinding.isArray()) {
            return getQualifiedTypeName(typeBinding.getElementType()) + "[]";
        }
        
        if (typeBinding.isParameterizedType()) {
            ITypeBinding erasure = typeBinding.getErasure();
            if (erasure != null) {
                return erasure.getQualifiedName();
            }
        }
        
        if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        }
        
        String qualifiedName = typeBinding.getQualifiedName();
        return qualifiedName != null ? qualifiedName : typeBinding.getName();
    }
    
    private List<String> extractModifiers(int modifiers) {
        List<String> modifierList = new ArrayList<>();
        if (Modifier.isPublic(modifiers)) modifierList.add("public");
        if (Modifier.isPrivate(modifiers)) modifierList.add("private");
        if (Modifier.isProtected(modifiers)) modifierList.add("protected");
        if (Modifier.isStatic(modifiers)) modifierList.add("static");
        if (Modifier.isFinal(modifiers)) modifierList.add("final");
        if (Modifier.isAbstract(modifiers)) modifierList.add("abstract");
        if (Modifier.isSynchronized(modifiers)) modifierList.add("synchronized");
        if (Modifier.isVolatile(modifiers)) modifierList.add("volatile");
        if (Modifier.isTransient(modifiers)) modifierList.add("transient");
        if (Modifier.isNative(modifiers)) modifierList.add("native");
        return modifierList;
    }
}
