package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.CodeRelationship;
import com.poseidon.codegraph.model.CodeUnit;
import com.poseidon.codegraph.model.RelationshipType;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;


/**
 * 结构关系构建器
 * 
 * 职责：
 * - 在 AST 遍历完成后，基于已提取的节点构建结构关系
 * - PACKAGE_TO_UNIT: Package -> Unit
 * - UNIT_TO_FUNCTION: Unit -> Function
 * 
 * 优先级：100（关系构建阶段，在所有节点提取完成后）
 */
@Slf4j
public class StructureRelationshipProcessor implements ASTNodeProcessor {
    
    @Override
    public void onTraversalComplete(ProcessorContext context) {
        CodeGraph graph = context.getGraph();
        
        log.debug("开始构建结构关系...");
        
        int packageToUnitCount = 0;
        int unitToFunctionCount = 0;
        
        // 1. 构建 PACKAGE_TO_UNIT 关系
        for (CodeUnit unit : graph.getUnitsAsList()) {
            if (unit.getPackageId() != null && !unit.getPackageId().isEmpty()) {
                CodeRelationship rel = new CodeRelationship();
                rel.setRelationshipType(RelationshipType.PACKAGE_TO_UNIT);
                rel.setFromNodeId(unit.getPackageId());
                rel.setToNodeId(unit.getId());
                rel.setId(JdtGraphIds.relationshipId(unit.getPackageId(), RelationshipType.PACKAGE_TO_UNIT, unit.getId()));
                rel.setLanguage("java");
                graph.addRelationship(rel);
                packageToUnitCount++;
            }
        }
        
        // 2. 构建 UNIT_TO_FUNCTION 关系
        for (CodeUnit unit : graph.getUnitsAsList()) {
            for (CodeFunction function : unit.getFunctions()) {
                CodeRelationship rel = new CodeRelationship();
                rel.setRelationshipType(RelationshipType.UNIT_TO_FUNCTION);
                rel.setFromNodeId(unit.getId());
                rel.setToNodeId(function.getId());
                rel.setId(JdtGraphIds.relationshipId(unit.getId(), RelationshipType.UNIT_TO_FUNCTION, function.getId()));
                rel.setLanguage("java");
                graph.addRelationship(rel);
                unitToFunctionCount++;
            }
        }
        
        log.info("✓ 结构关系构建完成: PACKAGE_TO_UNIT={}, UNIT_TO_FUNCTION={}",
            packageToUnitCount, unitToFunctionCount);
    }
    
    @Override
    public int getPriority() {
        return 100;  // 关系构建阶段
    }
    
    @Override
    public String getName() {
        return "StructureRelationshipProcessor";
    }
}
