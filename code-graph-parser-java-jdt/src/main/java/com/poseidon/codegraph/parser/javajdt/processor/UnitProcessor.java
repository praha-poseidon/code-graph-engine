package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit 节点提取器（Class/Interface/Enum）
 * 
 * 职责：
 * - 从 AST 中提取 Unit 节点
 * - 记录 Unit 与 Package 的关联（packageId）
 * - 不创建关系（关系由 StructureRelationshipProcessor 统一创建）
 * 
 * 优先级：2（节点提取阶段，在 Package 之后）
 */
@Slf4j
public class UnitProcessor implements ASTNodeProcessor {
    
    @Override
    public void onTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        CodeUnit unit = new CodeUnit();
        unit.setName(type.getName().getIdentifier());
        
        ITypeBinding binding = type.resolveBinding();
        if (binding == null) {
            log.warn("类型绑定解析失败，使用源码信息降级创建 Unit: type={}", unit.getName());
            String prefix = context.getPackageName() != null && !context.getPackageName().isBlank()
                    ? context.getPackageName() + "."
                    : "";
            unit.setQualifiedName(prefix + unit.getName());
            unit.setId(JdtGraphIds.unitId(unit.getQualifiedName()));
            unit.setUnitType(resolveUnitType(type, null));
        } else {
            unit.setQualifiedName(binding.getQualifiedName());
            unit.setId(JdtGraphIds.unitId(unit.getQualifiedName()));
            unit.setUnitType(resolveUnitType(type, binding));
        }
        
        int modifiers = type.getModifiers();
        unit.setModifiers(extractModifiers(modifiers));
        unit.setIsAbstract(Modifier.isAbstract(modifiers));
        
        unit.setProjectFilePath(context.getProjectFilePath());
        unit.setStartLine(context.getCompilationUnit().getLineNumber(type.getStartPosition()));
        unit.setEndLine(context.getCompilationUnit().getLineNumber(type.getStartPosition() + type.getLength()));
        unit.setLanguage("java");
        unit.setGitRepoUrl(context.getGitRepoUrl());
        unit.setGitBranch(context.getGitBranch());
        
        // 记录 Unit 所属的 Package（用于后续关系构建）
        if (context.getPackageName() != null) {
            unit.setPackageId(JdtGraphIds.packageId(context.getPackageName()));
        }
        
        // 只添加节点，不创建关系
        context.getGraph().addUnit(unit);
        
        log.debug("✓ 提取 Unit: {}, type: {}", unit.getName(), unit.getUnitType());
    }
    
    @Override
    public int getPriority() {
        return 2;  // 节点提取阶段
    }
    
    @Override
    public String getName() {
        return "UnitProcessor";
    }
    
    // ========== 辅助方法 ==========
    
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

    private String resolveUnitType(AbstractTypeDeclaration type, ITypeBinding binding) {
        if (type instanceof EnumDeclaration || (binding != null && binding.isEnum())) {
            return "enum";
        }
        if (type instanceof AnnotationTypeDeclaration || (binding != null && binding.isAnnotation())) {
            return "annotation";
        }
        if (type instanceof TypeDeclaration typeDeclaration && typeDeclaration.isInterface()) {
            return "interface";
        }
        return "class";
    }
}
