package com.poseidon.codegraph.parser.javajdt.processor;

import com.poseidon.codegraph.model.CodePackage;
import com.poseidon.codegraph.parser.javajdt.ASTNodeProcessor;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.ProcessorContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.PackageDeclaration;

import java.nio.file.Path;

/**
 * Package 节点提取器
 * 
 * 职责：
 * - 从 AST 中提取 Package 节点
 * - 不创建关系（关系由 StructureRelationshipProcessor 统一创建）
 * 
 * 优先级：1（节点提取阶段）
 */
@Slf4j
public class PackageProcessor implements ASTNodeProcessor {
    
    @Override
    public void onPackage(PackageDeclaration pkg, ProcessorContext context) {
        String packageName = pkg.getName().getFullyQualifiedName();
        
        CodePackage codePackage = new CodePackage();
        codePackage.setId(JdtGraphIds.packageId(packageName));
        codePackage.setName(packageName);
        codePackage.setQualifiedName(packageName);
        codePackage.setPackagePath(packageName.replace('.', '/'));
        
        Path path = Path.of(context.getProjectFilePath());
        String packageDir = path.getParent() != null ? path.getParent().toString() : "";
        codePackage.setProjectFilePath(packageDir.replace('\\', '/'));
        
        codePackage.setLanguage("java");
        codePackage.setGitRepoUrl(context.getGitRepoUrl());
        codePackage.setGitBranch(context.getGitBranch());
        
        // 只添加节点，不创建关系
        context.getGraph().addPackage(codePackage);
        
        log.debug("✓ 提取 Package: {}", packageName);
    }
    
    @Override
    public int getPriority() {
        return 1;  // 节点提取阶段
    }
    
    @Override
    public String getName() {
        return "PackageProcessor";
    }
}
