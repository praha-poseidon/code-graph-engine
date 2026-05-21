package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

/**
 * JDT-specific graph ID builder.
 */
public final class JdtGraphIds {

    private JdtGraphIds() {
    }

    public static String packageId(String packageName) {
        return GraphIds.packageId(packageName);
    }

    public static String unitId(String qualifiedName) {
        return GraphIds.unitId(qualifiedName);
    }

    public static String functionId(String qualifiedSignature) {
        return GraphIds.functionId(qualifiedSignature);
    }

    public static String relationshipId(String fromNodeId, RelationshipType type, String toNodeId) {
        return GraphIds.relationshipId(fromNodeId, type, toNodeId);
    }

    public static String qualifiedMethodSignature(IMethodBinding binding) {
        ITypeBinding declaringClass = binding != null ? binding.getDeclaringClass() : null;
        if (binding == null || declaringClass == null) {
            return binding != null ? binding.getName() + "()" : "unknown()";
        }
        StringBuilder qualified = new StringBuilder();
        qualified.append(typeName(declaringClass));
        qualified.append(".");
        qualified.append(methodSignature(binding));
        return qualified.toString();
    }

    public static String sourceQualifiedMethodSignature(String unitQualifiedName, MethodDeclaration method) {
        return unitQualifiedName + "." + sourceMethodSignature(method);
    }

    public static String methodSignature(IMethodBinding binding) {
        StringBuilder signature = new StringBuilder(binding.getName());
        signature.append("(");
        ITypeBinding[] paramTypes = binding.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                signature.append(",");
            }
            signature.append(typeName(paramTypes[i]));
        }
        signature.append(")");
        return signature.toString();
    }

    public static String sourceMethodSignature(MethodDeclaration method) {
        StringBuilder signature = new StringBuilder();
        signature.append(method.getName().getIdentifier());
        signature.append("(");
        for (int i = 0; i < method.parameters().size(); i++) {
            if (i > 0) {
                signature.append(",");
            }
            Object parameter = method.parameters().get(i);
            if (parameter instanceof SingleVariableDeclaration variable) {
                signature.append(variable.getType());
            } else {
                signature.append("unknown");
            }
        }
        signature.append(")");
        return signature.toString();
    }

    public static String typeName(ITypeBinding binding) {
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
