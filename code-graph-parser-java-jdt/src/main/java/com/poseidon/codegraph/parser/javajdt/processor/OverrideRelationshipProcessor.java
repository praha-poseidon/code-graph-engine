package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.util.HashSet;
import java.util.Set;

/**
 * 方法重写关系构建器。
 */
@Slf4j
public class OverrideRelationshipProcessor implements ASTNodeProcessor {

    @Override
    public void onMethodDeclaration(
            MethodDeclaration method,
            AbstractTypeDeclaration enclosingType,
            ProcessorContext context) {
        IMethodBinding methodBinding = method.resolveBinding();
        if (methodBinding == null) {
            return;
        }

        String overridingId = methodId(methodBinding);
        if (overridingId == null || overridingId.isBlank()) {
            return;
        }

        Set<String> visitedTypes = new HashSet<>();
        addOverridesFromType(methodBinding, methodBinding.getDeclaringClass().getSuperclass(), overridingId, visitedTypes, context);
        for (ITypeBinding interfaceBinding : methodBinding.getDeclaringClass().getInterfaces()) {
            addOverridesFromType(methodBinding, interfaceBinding, overridingId, visitedTypes, context);
        }
    }

    private void addOverridesFromType(
            IMethodBinding overriding,
            ITypeBinding targetType,
            String overridingId,
            Set<String> visitedTypes,
            ProcessorContext context) {
        if (targetType == null) {
            return;
        }

        ITypeBinding erasure = targetType.getErasure();
        String typeId = erasure != null ? erasure.getQualifiedName() : targetType.getQualifiedName();
        if (typeId != null && !visitedTypes.add(typeId)) {
            return;
        }

        for (IMethodBinding candidate : targetType.getDeclaredMethods()) {
            if (overrides(overriding, candidate)) {
                addOverrideRelationship(overridingId, methodId(candidate), context);
            }
        }

        addOverridesFromType(overriding, targetType.getSuperclass(), overridingId, visitedTypes, context);
        for (ITypeBinding interfaceBinding : targetType.getInterfaces()) {
            addOverridesFromType(overriding, interfaceBinding, overridingId, visitedTypes, context);
        }
    }

    private boolean overrides(IMethodBinding overriding, IMethodBinding candidate) {
        IMethodBinding overridingDeclaration = overriding.getMethodDeclaration();
        IMethodBinding candidateDeclaration = candidate.getMethodDeclaration();
        if (overridingDeclaration.overrides(candidateDeclaration)) {
            return true;
        }
        return overridingDeclaration.getName().equals(candidateDeclaration.getName())
                && sameParameterTypes(
                        overridingDeclaration.getParameterTypes(),
                        candidateDeclaration.getParameterTypes());
    }

    private boolean sameParameterTypes(ITypeBinding[] left, ITypeBinding[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!JdtGraphIds.typeName(left[i]).equals(JdtGraphIds.typeName(right[i]))) {
                return false;
            }
        }
        return true;
    }

    private void addOverrideRelationship(String overridingId, String overriddenId, ProcessorContext context) {
        if (overriddenId == null || overriddenId.isBlank() || overridingId.equals(overriddenId)) {
            return;
        }
        CodeRelationship rel = new CodeRelationship();
        rel.setRelationshipType(RelationshipType.OVERRIDES);
        rel.setFromNodeId(overridingId);
        rel.setToNodeId(overriddenId);
        rel.setId(JdtGraphIds.relationshipId(overridingId, RelationshipType.OVERRIDES, overriddenId));
        rel.setLanguage("java");
        context.getGraph().addRelationship(rel);
    }

    private String methodId(IMethodBinding binding) {
        return JdtGraphIds.functionId(JdtGraphIds.qualifiedMethodSignature(binding));
    }

    @Override
    public int getPriority() {
        return 6;
    }

    @Override
    public String getName() {
        return "OverrideRelationshipProcessor";
    }
}
