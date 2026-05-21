package com.poseidon.codegraph.parser.javajdt.filter;

import com.poseidon.codegraph.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Getter/Setter 过滤器
 * 过滤掉 JavaBean 风格的属性访问方法。
 */
public class GetterSetterFilter implements RelationshipFilter {

    @Override
    public boolean shouldKeep(CodeRelationship relationship, IMethodBinding targetBinding) {
        String methodName = targetBinding.getName();

        if (isBeanSetter(methodName, targetBinding)) {
            return false;
        }

        if (isBeanGetter(methodName, targetBinding)) {
            return false;
        }

        return true;
    }

    private boolean isBeanSetter(String methodName, IMethodBinding binding) {
        return methodName.startsWith("set")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && binding.getParameterTypes().length == 1
                && isVoid(binding.getReturnType());
    }

    private boolean isBeanGetter(String methodName, IMethodBinding binding) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.isUpperCase(methodName.charAt(3))
                    && binding.getParameterTypes().length == 0
                    && !isVoid(binding.getReturnType());
        }

        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.isUpperCase(methodName.charAt(2))
                    && binding.getParameterTypes().length == 0
                    && isBoolean(binding.getReturnType());
        }

        return false;
    }

    private boolean isVoid(ITypeBinding type) {
        return type == null || "void".equals(type.getName());
    }

    private boolean isBoolean(ITypeBinding type) {
        if (type == null) {
            return false;
        }
        String name = type.getQualifiedName();
        return "boolean".equals(type.getName()) || "java.lang.Boolean".equals(name);
    }
}
