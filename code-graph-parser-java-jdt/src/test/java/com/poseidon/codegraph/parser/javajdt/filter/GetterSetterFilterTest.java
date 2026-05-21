package com.poseidon.codegraph.parser.javajdt.filter;

import com.poseidon.codegraph.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetterSetterFilterTest {

    private final GetterSetterFilter filter = new GetterSetterFilter();

    @Test
    void filtersOnlyJavaBeanAccessorSignaturesWhenTargetSourceIsUnavailable() {
        assertThat(filter.shouldKeep(new CodeRelationship(), method("getName", type("java.lang.String", "String"))))
            .isFalse();
        assertThat(filter.shouldKeep(new CodeRelationship(), method("isEnabled", type("boolean", "boolean"))))
            .isFalse();
        assertThat(filter.shouldKeep(new CodeRelationship(), method("setName", type("void", "void"), type("java.lang.String", "String"))))
            .isFalse();
    }

    @Test
    void keepsMethodsThatOnlyLookLikeAccessorsByPrefix() {
        assertThat(filter.shouldKeep(new CodeRelationship(), method("get", type("java.lang.String", "String"))))
            .isTrue();
        assertThat(filter.shouldKeep(new CodeRelationship(), method("setup", type("void", "void"), type("java.lang.String", "String"))))
            .isTrue();
        assertThat(filter.shouldKeep(new CodeRelationship(), method("setStatus", type("java.lang.String", "String"), type("java.lang.String", "String"))))
            .isTrue();
        assertThat(filter.shouldKeep(new CodeRelationship(), method("isReady", type("java.lang.String", "String"))))
            .isTrue();
    }

    private IMethodBinding method(String name, ITypeBinding returnType, ITypeBinding... parameters) {
        IMethodBinding binding = mock(IMethodBinding.class);
        when(binding.getName()).thenReturn(name);
        when(binding.getReturnType()).thenReturn(returnType);
        when(binding.getParameterTypes()).thenReturn(parameters);
        return binding;
    }

    private ITypeBinding type(String qualifiedName, String name) {
        ITypeBinding binding = mock(ITypeBinding.class);
        when(binding.getQualifiedName()).thenReturn(qualifiedName);
        when(binding.getName()).thenReturn(name);
        return binding;
    }
}
