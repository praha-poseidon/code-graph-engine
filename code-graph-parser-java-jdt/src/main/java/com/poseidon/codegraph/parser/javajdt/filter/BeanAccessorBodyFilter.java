package com.poseidon.codegraph.parser.javajdt.filter;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;

/**
 * 判断方法声明是否是无业务逻辑的 JavaBean getter/setter。
 */
public class BeanAccessorBodyFilter {

    public boolean isBeanAccessor(MethodDeclaration method, IMethodBinding binding) {
        if (method == null || binding == null || method.getBody() == null) {
            return false;
        }
        String methodName = binding.getName();
        if (isGetterName(methodName, binding)) {
            return isTrivialGetter(method);
        }
        if (isSetterName(methodName, binding)) {
            return isTrivialSetter(method);
        }
        return false;
    }

    private boolean isGetterName(String methodName, IMethodBinding binding) {
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

    private boolean isSetterName(String methodName, IMethodBinding binding) {
        return methodName.startsWith("set")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && binding.getParameterTypes().length == 1
                && isVoid(binding.getReturnType());
    }

    private boolean isTrivialGetter(MethodDeclaration method) {
        if (method.getBody().statements().size() != 1) {
            return false;
        }
        Statement statement = (Statement) method.getBody().statements().get(0);
        if (!(statement instanceof ReturnStatement returnStatement)) {
            return false;
        }
        return isFieldRead(returnStatement.getExpression());
    }

    private boolean isTrivialSetter(MethodDeclaration method) {
        if (method.getBody().statements().size() != 1 || method.parameters().size() != 1) {
            return false;
        }
        Statement statement = (Statement) method.getBody().statements().get(0);
        if (!(statement instanceof ExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof Assignment assignment)) {
            return false;
        }
        Object parameter = method.parameters().get(0);
        if (!(parameter instanceof SingleVariableDeclaration variable)) {
            return false;
        }
        return isFieldWrite(assignment.getLeftHandSide())
                && isParameterReference(assignment.getRightHandSide(), variable.getName().getIdentifier());
    }

    private boolean isFieldRead(Expression expression) {
        if (expression instanceof FieldAccess) {
            return true;
        }
        if (expression instanceof SimpleName simpleName) {
            return simpleName.resolveBinding() instanceof org.eclipse.jdt.core.dom.IVariableBinding variableBinding
                    && variableBinding.isField();
        }
        return false;
    }

    private boolean isFieldWrite(Expression expression) {
        if (expression instanceof FieldAccess) {
            return true;
        }
        if (expression instanceof SimpleName simpleName) {
            return simpleName.resolveBinding() instanceof org.eclipse.jdt.core.dom.IVariableBinding variableBinding
                    && variableBinding.isField();
        }
        return false;
    }

    private boolean isParameterReference(Expression expression, String parameterName) {
        return expression instanceof SimpleName simpleName
                && parameterName.equals(simpleName.getIdentifier());
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
