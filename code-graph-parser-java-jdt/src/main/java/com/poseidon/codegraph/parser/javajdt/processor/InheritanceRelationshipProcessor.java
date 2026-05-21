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
 * 继承关系构建器。
 */
@Slf4j
public class InheritanceRelationshipProcessor implements ASTNodeProcessor {

    @Override
    public void onTypeDeclaration(AbstractTypeDeclaration type, ProcessorContext context) {
        ITypeBinding binding = type.resolveBinding();
        if (binding == null) {
            return;
        }

        ITypeBinding superClass = binding.getSuperclass();
        if (superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName())) {
            addRelationship(binding, superClass, RelationshipType.EXTENDS, context);
        }

        if (binding.isInterface()) {
            for (ITypeBinding superInterface : binding.getInterfaces()) {
                addRelationship(binding, superInterface, RelationshipType.EXTENDS, context);
            }
        }
    }

    private void addRelationship(
            ITypeBinding from,
            ITypeBinding to,
            RelationshipType relationshipType,
            ProcessorContext context) {
        String fromId = typeId(from);
        String toId = typeId(to);
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            return;
        }

        CodeRelationship rel = new CodeRelationship();
        rel.setRelationshipType(relationshipType);
        rel.setFromNodeId(fromId);
        rel.setToNodeId(toId);
        rel.setId(JdtGraphIds.relationshipId(fromId, relationshipType, toId));
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
        return "InheritanceRelationshipProcessor";
    }
}
