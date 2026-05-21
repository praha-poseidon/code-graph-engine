package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;


/**
 * 接口实现关系构建器。
 */
@Slf4j
public class ImplementationRelationshipProcessor implements ASTNodeProcessor {

    @Override
    public void onTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        ITypeBinding binding = type.resolveBinding();
        if (binding == null || binding.isInterface()) {
            return;
        }

        for (ITypeBinding interfaceBinding : binding.getInterfaces()) {
            addRelationship(binding, interfaceBinding, context);
        }
    }

    private void addRelationship(ITypeBinding from, ITypeBinding to, ProcessorContext context) {
        String fromId = typeId(from);
        String toId = typeId(to);
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            return;
        }

        CodeRelationship rel = new CodeRelationship();
        rel.setRelationshipType(RelationshipType.IMPLEMENTS);
        rel.setFromNodeId(fromId);
        rel.setToNodeId(toId);
        rel.setId(JdtGraphIds.relationshipId(fromId, RelationshipType.IMPLEMENTS, toId));
        rel.setLanguage("java");
        context.getGraph().addRelationship(rel);
    }

    private String typeId(ITypeBinding binding) {
        ITypeBinding erasure = binding != null ? binding.getErasure() : null;
        String qualifiedName = erasure != null ? erasure.getQualifiedName() : null;
        String qualified = qualifiedName != null && !qualifiedName.isBlank() ? qualifiedName : binding != null ? binding.getName() : null;
        return qualified != null ? JdtGraphIds.unitId(qualified) : null;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getName() {
        return "ImplementationRelationshipProcessor";
    }
}
