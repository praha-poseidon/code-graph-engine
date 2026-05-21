package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.parser.javajdt.filter.FilterPipeline;
import com.poseidon.codegraph.parser.javajdt.filter.GetterSetterFilter;
import com.poseidon.codegraph.parser.javajdt.filter.PackageWhitelistFilter;
import com.poseidon.codegraph.model.*;
import com.poseidon.codegraph.parser.javajdt.endpoint.EndpointParsingService;
import com.poseidon.codegraph.parser.javajdt.endpoint.support.ExternalConfigValueScanner;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.JavaCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * 基于 JDT 的源码解析器实现
 */
@Slf4j
public class JdtSourceCodeParser extends AbstractSourceCodeParser {
    
    private final String[] classpathEntries;
    private final String[] sourcepathEntries;
    private final ASTTraverser traverser;
    private final Map<String, Map<String, List<String>>> externalValues;
    private final EndpointParsingService endpointParsingService;
    
    // 司内包前缀白名单（只解析源码和这些包中的方法调用）
    private static final Set<String> INTERNAL_PACKAGE_PREFIXES = Set.of(
        "com.poseidon."
    );
    
    /**
     * 构造函数（使用 ProcessorRegistry）
     */
    public JdtSourceCodeParser(String[] classpathEntries, String[] sourcepathEntries, ProcessorRegistry registry) {
        this(classpathEntries, sourcepathEntries, registry, null);
    }

    public JdtSourceCodeParser(
            String[] classpathEntries,
            String[] sourcepathEntries,
            ProcessorRegistry registry,
            Map<String, Map<String, List<String>>> externalValues) {
        this(classpathEntries, sourcepathEntries, registry, externalValues, null);
    }

    public JdtSourceCodeParser(
            String[] classpathEntries,
            String[] sourcepathEntries,
            ProcessorRegistry registry,
            Map<String, Map<String, List<String>>> externalValues,
            EndpointParsingService endpointParsingService) {
        super(new FilterPipeline()
            .addFilter(new GetterSetterFilter())
            .addFilter(new PackageWhitelistFilter(INTERNAL_PACKAGE_PREFIXES)));
        this.classpathEntries = classpathEntries;
        this.sourcepathEntries = sourcepathEntries;
        this.traverser = new ASTTraverser(registry.getAll());
        this.externalValues = externalValues;
        this.endpointParsingService = endpointParsingService;
    }
    
    /**
     * 便捷构造函数（使用默认 Processor：核心解析 + 端点解析）
     */
    public JdtSourceCodeParser(String[] classpathEntries, String[] sourcepathEntries) {
        this(classpathEntries, sourcepathEntries, ProcessorRegistry.createWithEndpoint());
    }
    
    private CompilationUnit createAST(String absoluteFilePath) {
        try {
            log.debug("开始解析文件: {}", absoluteFilePath);
            String source = Files.readString(Path.of(absoluteFilePath));
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);

            /* ==================== 关键修复：设置编译器选项 ====================
             * 
             * 问题描述：
             *   Lambda 表达式中的方法调用无法被识别，例如：
             *   .getOrElse(() -> queryFromMailFromRemote(...))
             *   其中 queryFromMailFromRemote 方法调用的 resolveMethodBinding() 返回 null
             * 
             * 根本原因：
             *   1. AST.getJLSLatest() 只指定了**语法解析级别**，JDT 能识别 lambda 的语法结构
             *   2. 但**类型推断引擎**需要知道用哪个 Java 版本的语义规则来推断类型
             *   3. 不设置 CompilerOptions 时，JDT 默认使用 Java 1.4/1.6 的规则
             *   4. 那时还没有 lambda 表达式和现代泛型推断（如 Supplier<? extends T>）
             *   5. 所以遇到复杂的 lambda + 泛型场景，类型推断失败，返回 null
             * 
             * 具体场景：
             *   Option<GroupPojo>.map(Mono::just)
             *                    .getOrElse(() -> queryFromMailFromRemote(...))
             *   
             *   - getOrElse 参数类型：Supplier<? extends Mono<GroupPojo>>
             *   - 需要推断 lambda 内部 queryFromMailFromRemote 的返回类型
             *   - 需要验证类型兼容性（Mono<GroupPojo> 是否匹配）
             *   - 这种复杂的泛型 + lambda 推断需要 Java 8+ 的类型推断规则
             * 
             * 解决方案：
             *   设置 CompilerOptions，明确告诉 JDT 使用 Java 8 的编译规则
             *   这样 JDT 的类型推断引擎会启用 Java 8 的 lambda 和泛型推断能力
             * 
             * 效果：
             *   修改前：relationships=27（lambda 中的调用被跳过）
             *   修改后：relationships=30（lambda 中的调用成功识别）
             *   queryMail -> queryFromMailFromRemote 调用关系成功建立 ✓
             * 
             * 参考：
             *   JDT 文档说明需要同时满足：
             *   - setResolveBindings(true)  ← 已有
             *   - setEnvironment(...)       ← 已有
             *   - setCompilerOptions(...)   ← 本次新增
             * ===================================================================
             */
            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
            parser.setCompilerOptions(options);
            log.debug("已设置编译器选项: Java 17（支持 lambda、泛型推断和 record 等现代语法）");
            
            String[] fullClasspath = buildFullClasspath();
            validateEnvironmentEntries("classpath", fullClasspath);
            validateEnvironmentEntries("sourcepath", sourcepathEntries);
            
            boolean hasSourcepath = sourcepathEntries != null && sourcepathEntries.length > 0;
            if (fullClasspath.length > 0 || hasSourcepath) {
                log.debug("启用绑定解析: classpathCount={}, sourcepathCount={}",
                          fullClasspath.length,
                          sourcepathEntries != null ? sourcepathEntries.length : 0);
                parser.setResolveBindings(true);
                parser.setBindingsRecovery(true);
                parser.setEnvironment(
                    fullClasspath,
                    sourcepathEntries != null ? sourcepathEntries : new String[0],
                    null,
                    true
                );
                parser.setUnitName(absoluteFilePath);
            } else {
                log.warn("classpath/sourcepath 为空，禁用绑定解析: file={}, 这将导致类型绑定失败，数据可能不准确", absoluteFilePath);
                parser.setResolveBindings(false);
            }
            
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            log.debug("文件解析完成: {}", absoluteFilePath);
            return cu;
        } catch (IOException e) {
            log.error("文件读取失败: file={}, error={}", absoluteFilePath, e.getMessage());
            throw new RuntimeException("Failed to parse file: " + absoluteFilePath, e);
        } catch (Exception e) {
            log.error("AST 创建失败: file={}, error={}", absoluteFilePath, e.getMessage());
            throw new RuntimeException("Failed to create AST for file: " + absoluteFilePath, e);
        }
    }
    
    private String[] buildFullClasspath() {
        List<String> fullClasspath = new ArrayList<>();
        if (classpathEntries != null) {
            for (String entry : classpathEntries) {
                if (entry != null && !entry.isEmpty()) {
                    fullClasspath.add(entry);
                }
            }
        }
        return fullClasspath.toArray(new String[0]);
    }

    private void validateEnvironmentEntries(String name, String[] entries) {
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (!Files.exists(Path.of(entry))) {
                throw new IllegalArgumentException("JDT " + name + " entry does not exist: " + entry);
            }
        }
    }
    
    @Override
    public CodeGraph parse(String absoluteFilePath, String projectName, String projectFilePath,
                          String gitRepoUrl, String gitBranch) {
        log.info("开始解析代码图谱（使用 Processor 架构）: absoluteFile={}, projectFile={}, git={}/{}", 
                absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch);
        
        // 创建 AST
        CompilationUnit cu = createAST(absoluteFilePath);
        
        // 创建 ProcessorContext
        ProcessorContext context = new ProcessorContext();
        context.setCompilationUnit(cu);
        context.setAbsoluteFilePath(absoluteFilePath);
        context.setProjectName(projectName);
        context.setProjectFilePath(projectFilePath);
        context.setGitRepoUrl(gitRepoUrl);
        context.setGitBranch(gitBranch);
        context.setEndpointParsingService(endpointParsingService);
        context.setExternalValues(resolveExternalValues(absoluteFilePath, projectFilePath));
        
        // 使用 ASTTraverser 遍历并触发所有 Processor
        CodeGraph graph = traverser.traverse(context);
        
        log.info("代码图谱解析完成: file={}, packages={}, units={}, functions={}, relationships={}, endpoints={}",
                 projectFilePath,
                 graph.getPackagesAsList().size(),
                 graph.getUnitsAsList().size(),
                 graph.getFunctionsAsList().size(),
                 graph.getRelationshipsAsList().size(),
                 graph.getEndpointsAsList().size());
        
        return graph;
    }

    private Map<String, Map<String, List<String>>> resolveExternalValues(String absoluteFilePath, String projectFilePath) {
        if (externalValues != null && !externalValues.isEmpty()) {
            return externalValues;
        }
        return ExternalConfigValueScanner.scan(absoluteFilePath, projectFilePath);
    }
    
    @Override
    public List<CodePackage> parsePackages(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parsePackages(cu, absoluteFilePath, projectName, projectFilePath);
    }
    
    @Override
    public List<CodeUnit> parseUnits(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parseUnits(cu, absoluteFilePath, projectName, projectFilePath);
    }
    
    @Override
    public List<CodeFunction> parseFunctions(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        List<CodeUnit> units = parseUnits(cu, absoluteFilePath, projectName, projectFilePath);
        List<CodeFunction> functions = new ArrayList<>();
        for (CodeUnit unit : units) {
            functions.addAll(unit.getFunctions());
        }
        return functions;
    }
    
    @Override
    public List<CodeRelationship> parseRelationships(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parseRelationships(cu, projectName, projectFilePath);
    }
    
    private List<CodePackage> parsePackages(CompilationUnit cu, String absoluteFilePath, String projectName, String projectFilePath) {
        List<CodePackage> packages = new ArrayList<>();
        String packageName = extractPackageName(cu);
        if (packageName != null && !packageName.isEmpty()) {
            CodePackage pkg = new CodePackage();
            pkg.setId(JdtGraphIds.packageId(packageName));
            pkg.setName(packageName);
            pkg.setQualifiedName(packageName);
            pkg.setPackagePath(packageName.replace('.', '/'));
            
            Path path = Path.of(projectFilePath);
            String packageDir = path.getParent() != null ? path.getParent().toString() : "";
            pkg.setProjectFilePath(packageDir.replace('\\', '/'));
            pkg.setLanguage("java");
            packages.add(pkg);
        }
        return packages;
    }
    
    private List<CodeUnit> parseUnits(CompilationUnit cu, String absoluteFilePath, String projectName, String projectFilePath) {
        List<CodeUnit> units = new ArrayList<>();
        String packageName = extractPackageName(cu);
        
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration) {
                units.add(parseTypeDeclaration((TypeDeclaration) type, projectName, projectFilePath, packageName, cu));
            } else if (type instanceof EnumDeclaration) {
                units.add(parseEnumDeclaration((EnumDeclaration) type, projectName, projectFilePath, packageName, cu));
            } else if (type instanceof AnnotationTypeDeclaration) {
                units.add(parseAnnotationTypeDeclaration((AnnotationTypeDeclaration) type, projectName, projectFilePath, packageName, cu));
            }
        }
        return units;
    }
    
    private CodeUnit parseTypeDeclaration(TypeDeclaration typeDecl, String projectName, String projectFilePath, 
                                         String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(typeDecl.getName().getIdentifier());
        
        ITypeBinding binding = typeDecl.resolveBinding();
        if (binding == null) {
            log.error("类型绑定解析失败: class={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        unit.setId(JdtGraphIds.unitId(unit.getQualifiedName()));
        unit.setUnitType(typeDecl.isInterface() ? "interface" : "class");
        
        int modifiers = typeDecl.getModifiers();
        unit.setModifiers(extractModifiers(modifiers));
        unit.setIsAbstract(Modifier.isAbstract(modifiers));
        
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(typeDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(typeDecl.getStartPosition() + typeDecl.getLength()));
        unit.setLanguage("java");
        unit.setPackageId(JdtGraphIds.packageId(packageName));
        
        for (MethodDeclaration method : typeDecl.getMethods()) {
            CodeFunction function = parseMethodDeclaration(method, unit, projectName, cu);
            unit.addFunction(function);
        }
        
        return unit;
    }
    
    private CodeUnit parseEnumDeclaration(EnumDeclaration enumDecl, String projectName, String projectFilePath,
                                         String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(enumDecl.getName().getIdentifier());
        
        ITypeBinding binding = enumDecl.resolveBinding();
        if (binding == null) {
            log.error("枚举类型绑定解析失败: enum={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("枚举类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        unit.setId(JdtGraphIds.unitId(unit.getQualifiedName()));
        unit.setUnitType("enum");
        unit.setModifiers(extractModifiers(enumDecl.getModifiers()));
        unit.setIsAbstract(false);
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(enumDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(enumDecl.getStartPosition() + enumDecl.getLength()));
        unit.setLanguage("java");
        unit.setPackageId(JdtGraphIds.packageId(packageName));
        
        return unit;
    }
    
    private CodeUnit parseAnnotationTypeDeclaration(AnnotationTypeDeclaration annoDecl, String projectName, String projectFilePath,
                                                   String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(annoDecl.getName().getIdentifier());
        
        ITypeBinding binding = annoDecl.resolveBinding();
        if (binding == null) {
            log.error("注解类型绑定解析失败: annotation={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("注解类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        unit.setId(JdtGraphIds.unitId(unit.getQualifiedName()));
        unit.setUnitType("annotation");
        unit.setModifiers(extractModifiers(annoDecl.getModifiers()));
        unit.setIsAbstract(false);
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(annoDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(annoDecl.getStartPosition() + annoDecl.getLength()));
        unit.setLanguage("java");
        unit.setPackageId(JdtGraphIds.packageId(packageName));
        
        return unit;
    }
    
    private CodeFunction parseMethodDeclaration(MethodDeclaration method, CodeUnit unit, String projectName, CompilationUnit cu) {
        CodeFunction function = new CodeFunction();
        function.setName(method.getName().getIdentifier());
        
        String signature = buildMethodSignature(method);
        function.setSignature(signature);
        
        String qualifiedName = unit.getQualifiedName() + "." + signature;
        function.setQualifiedName(qualifiedName);
        function.setId(JdtGraphIds.functionId(qualifiedName));
        
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.error("方法绑定解析失败，无法获取返回类型: method={}", function.getName());
            throw new RuntimeException("方法绑定解析失败: " + function.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        ITypeBinding returnTypeBinding = binding.getReturnType();
        if (returnTypeBinding != null) {
            function.setReturnType(getQualifiedTypeName(returnTypeBinding));
        } else {
            function.setReturnType("void");
        }
        
        int modifiers = method.getModifiers();
        function.setModifiers(extractModifiers(modifiers));
        function.setIsStatic(Modifier.isStatic(modifiers));
        function.setIsConstructor(method.isConstructor());
        function.setIsPlaceholder(false);
        function.setIsAsync(false);
        
        function.setProjectFilePath(unit.getProjectFilePath());
        function.setStartLine(cu.getLineNumber(method.getStartPosition()));
        function.setEndLine(cu.getLineNumber(method.getStartPosition() + method.getLength()));
        function.setLanguage("java");
        
        return function;
    }
    
    private List<CodeRelationship> parseRelationships(CompilationUnit cu, String projectName, String projectFilePath) {
        List<CodeRelationship> relationships = new ArrayList<>();
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                String methodName = node.getName().getIdentifier();
                int lineNumber = cu.getLineNumber(node.getStartPosition());

                IMethodBinding targetBinding = node.resolveMethodBinding();
                if (targetBinding == null) {
                    log.debug("方法目标绑定失败（跳过）: file={}, line={}, method={}", 
                              projectFilePath, lineNumber, methodName);
                    return true;
                }
                
                // 硬编码过滤逻辑已移除，改用 PackageWhitelistFilter
                
                ASTNode current = node.getParent();
                while (current != null && !(current instanceof MethodDeclaration)) {
                    current = current.getParent();
                }
                
                if (!(current instanceof MethodDeclaration)) {
                    log.warn("无法找到调用者方法声明（跳过）: file={}, line={}, method={}", 
                              projectFilePath, lineNumber, methodName);
                    return true;
                }
                
                MethodDeclaration callerMethod = (MethodDeclaration) current;
                IMethodBinding callerBinding = callerMethod.resolveBinding();
                
                if (callerBinding == null) {
                    String callerMethodName = callerMethod.getName().getIdentifier();
                    log.warn("调用者绑定失败（跳过）: file={}, line={}, callerMethod={}", 
                              projectFilePath, lineNumber, callerMethodName);
                    return true;
                }
                
                CodeRelationship rel = new CodeRelationship();
                
                String callerQualifiedName = buildQualifiedName(callerBinding);
                String fromNodeId = JdtGraphIds.functionId(callerQualifiedName);
                rel.setFromNodeId(fromNodeId);
                
                String targetQualifiedName = buildQualifiedName(targetBinding);
                String toNodeId = JdtGraphIds.functionId(targetQualifiedName);
                rel.setToNodeId(toNodeId);
                
                rel.setRelationshipType(RelationshipType.CALLS);
                rel.setId(JdtGraphIds.relationshipId(fromNodeId, RelationshipType.CALLS, toNodeId));
                rel.setLineNumber(lineNumber);
                rel.setCallType(Modifier.isStatic(targetBinding.getModifiers()) ? "static" : "virtual");
                rel.setLanguage("java");
                
                if (!shouldKeepRelationship(rel, targetBinding)) {
                    log.debug("过滤掉关系: {} -> {}", fromNodeId, toNodeId);
                    return true;
                }
                
                relationships.add(rel);
                
                log.debug("解析调用关系成功: {}:{} -> {}", projectFilePath, lineNumber, toNodeId);
                return true;
            }
        });
        
        log.info("文件调用关系解析完成: file={}, relationshipCount={}", projectFilePath, relationships.size());
        return relationships;
    }
    
    private String extractPackageName(CompilationUnit cu) {
        PackageDeclaration packageDecl = cu.getPackage();
        if (packageDecl != null) {
            return packageDecl.getName().getFullyQualifiedName();
        }
        return "(default)";
    }
    
    private List<String> extractModifiers(int modifiers) {
        List<String> result = new ArrayList<>();
        if (Modifier.isPublic(modifiers)) result.add("public");
        if (Modifier.isPrivate(modifiers)) result.add("private");
        if (Modifier.isProtected(modifiers)) result.add("protected");
        if (Modifier.isStatic(modifiers)) result.add("static");
        if (Modifier.isFinal(modifiers)) result.add("final");
        if (Modifier.isAbstract(modifiers)) result.add("abstract");
        if (Modifier.isSynchronized(modifiers)) result.add("synchronized");
        return result;
    }
    
    private String buildMethodSignature(MethodDeclaration method) {
        String methodName = method.getName().getIdentifier();
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.error("方法绑定解析失败: method={}, 可能原因: classpath 不完整或 JDK 未正确配置。", methodName);
            throw new RuntimeException("方法绑定解析失败: " + methodName + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        return buildMethodSignatureFromBinding(binding);
    }
    
    private String buildMethodSignatureFromBinding(IMethodBinding binding) {
        StringBuilder sig = new StringBuilder(binding.getName()).append("(");
        ITypeBinding[] params = binding.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sig.append(", ");
            sig.append(getQualifiedTypeName(params[i]));
        }
        sig.append("):");
        ITypeBinding returnType = binding.getReturnType();
        if (returnType != null) {
            sig.append(getQualifiedTypeName(returnType));
        } else {
            sig.append("void");
        }
        return sig.toString();
    }
    
    private String buildQualifiedName(IMethodBinding binding) {
        StringBuilder sb = new StringBuilder();
        sb.append(binding.getDeclaringClass().getQualifiedName());
        sb.append(".");
        sb.append(buildMethodSignatureFromBinding(binding));
        return sb.toString();
    }
    
    private String getQualifiedTypeName(ITypeBinding typeBinding) {
        if (typeBinding.isArray()) {
            ITypeBinding elementType = typeBinding.getElementType();
            return getQualifiedTypeName(elementType) + "[]";
        } else if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        } else {
            // 使用擦除后的类型，避免泛型差异导致ID不一致
            return typeBinding.getErasure().getQualifiedName();
        }
    }
}
