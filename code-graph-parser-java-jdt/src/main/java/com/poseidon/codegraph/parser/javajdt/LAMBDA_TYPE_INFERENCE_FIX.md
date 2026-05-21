# Lambda 类型推断问题修复记录

## 问题描述

### 症状
Lambda 表达式中的方法调用无法被识别，导致调用关系缺失。

**示例代码：**
```java
public Mono<GroupPojo> queryMail(String mailId, String mailName, MemberToken memberToken) {
    return groupRepository.findByExternalSystemId("MAIL", ExternalSystemId.of(mailId), memberToken.getTenantId())
        .map(Mono::just)
        .getOrElse(() -> queryFromMailFromRemote(mailId, mailName, memberToken));
        //            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        //            这个方法调用无法识别
}
```

**表现：**
- `queryMail -> queryFromMailFromRemote` 的调用关系未被记录到 Neo4j
- 日志显示：`方法目标绑定失败（跳过）: line=61, method=queryFromMailFromRemote`
- `MethodInvocation.resolveMethodBinding()` 返回 `null`

---

## 根本原因

### JDT Parser 缺少编译器选项配置

**问题代码：**
```java
ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
parser.setSource(source.toCharArray());
parser.setResolveBindings(true);
parser.setBindingsRecovery(true);
parser.setEnvironment(classpath, sourcepath, null, true);
parser.setUnitName(absoluteFilePath);

// ❌ 缺少：parser.setCompilerOptions(...)

CompilationUnit cu = (CompilationUnit) parser.createAST(null);
```

### 为什么会导致问题？

#### 1. **AST JLS Level vs Compiler Options 的区别**

**`AST.getJLSLatest()`** - 语法级别
- 作用：告诉 JDT 能识别哪些 **语法结构**
- 效果：能解析 lambda 表达式的语法 `() -> {...}`
- 结果：AST 中有 `LambdaExpression` 节点 ✅

**`CompilerOptions`** - 语义级别
- 作用：告诉 JDT 用哪个 Java 版本的 **类型推断规则**
- 效果：决定如何推断 lambda 中的类型
- 默认：如果不设置，可能使用 **Java 1.4/1.6** 的规则（没有 lambda）
- 结果：复杂的 lambda + 泛型场景类型推断失败 ❌

#### 2. **具体场景分析**

```java
Option<GroupPojo>.map(Mono::just)           // 返回 Option<Mono<GroupPojo>>
                 .getOrElse(() -> queryFromMailFromRemote(...))
```

**类型推断过程：**
```
1. getOrElse 签名：getOrElse(Supplier<? extends T> supplier)
2. 当前泛型：T = Mono<GroupPojo>
3. Lambda 目标类型：Supplier<? extends Mono<GroupPojo>>
4. 需要推断：queryFromMailFromRemote 的返回类型是否匹配
5. 涉及：
   - Lambda 表达式类型推断（Java 8+）
   - 泛型通配符 <? extends T>（Java 5+）
   - 函数式接口 Supplier<T>（Java 8+）
```

**如果用 Java 1.4/1.6 规则：**
- 没有 lambda 表达式概念
- 泛型推断规则不完善
- 遇到复杂场景直接放弃 → `resolveMethodBinding()` 返回 `null`

---

## 解决方案

### 修改位置
文件：`JdtSourceCodeParser.java`  
方法：`createAST(String absoluteFilePath)`

### 修改内容

```java
// 在创建 AST 前，设置编译器选项
Map<String, String> options = JavaCore.getOptions();
JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
parser.setCompilerOptions(options);
```

### 完整代码

```java
private CompilationUnit createAST(String absoluteFilePath) {
    try {
        String source = Files.readString(Path.of(absoluteFilePath));
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        
        // ✅ 关键修复：设置编译器选项
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
        parser.setCompilerOptions(options);
        
        String[] fullClasspath = buildFullClasspath();
        
        if (fullClasspath.length > 0) {
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true);
            parser.setEnvironment(
                fullClasspath,
                sourcepathEntries != null ? sourcepathEntries : new String[0],
                null,
                true
            );
            parser.setUnitName(absoluteFilePath);
        }
        
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        return cu;
    }
    // ... 异常处理
}
```

---

## 验证结果

### 修改前
```
AST 遍历完成: relationships=27
Neo4j 查询结果：
  queryMail -> findByExternalSystemId ✓
  queryMail -> of ✓
  queryMail -> queryFromMailFromRemote ✗ (缺失)
```

### 修改后
```
AST 遍历完成: relationships=30 (+3条)
Neo4j 查询结果：
  queryMail -> findByExternalSystemId ✓
  queryMail -> of ✓
  queryMail -> queryFromMailFromRemote ✓ (成功识别)
```

---

## 为什么选择 Java 8

1. **项目使用 Lambda 表达式** → 最低需要 Java 8
2. **Spring Boot 2.1.3** → 需要 Java 8+
3. **Vavr 0.9.2** → 需要 Java 8+
4. **代码中使用了 Supplier、Mono 等 Java 8 特性**

---

## 关键知识点

### JDT Parser 完整配置三要素

要让 `resolveMethodBinding()` 正确工作，必须同时配置：

```java
// 1. 启用绑定解析
parser.setResolveBindings(true);
parser.setBindingsRecovery(true);

// 2. 提供环境（classpath + sourcepath）
parser.setEnvironment(classpath, sourcepath, null, true);
parser.setUnitName(filePath);

// 3. 设置编译器选项（本次修复）
Map<String, String> options = JavaCore.getOptions();
JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
parser.setCompilerOptions(options);
```

**三者缺一不可！**

### 类型绑定失败的常见原因

1. **未启用绑定解析** → `setResolveBindings(false)`
2. **Classpath 不完整** → 缺少依赖 jar
3. **未设置编译器选项**（本次问题）→ 使用了错误的 Java 版本规则
4. **Sourcepath 缺失** → 找不到源码中的其他类

---

## 影响范围

### 修复前受影响的场景
所有涉及 Lambda 表达式的方法调用都可能识别失败：

- `Supplier` / `Function` / `Consumer` 等函数式接口
- 方法引用：`Mono::just`
- 复杂泛型推断：`Option<T>`, `Mono<T>`, `Stream<T>` 等
- 链式调用中的 lambda

### 修复后的改善
- Lambda 内部的方法调用能正确识别
- 复杂的泛型 + lambda 场景类型推断成功
- 调用关系更完整

---

## 相关文件

- 修改文件：`code-graph-engine/src/main/java/com/poseidon/codegraph/engine/domain/parser/JdtSourceCodeParser.java`
- 新增依赖：`org.eclipse.jdt.core.JavaCore`
- 测试文件：`QueryGroupInfoService.java`

---

## 参考

- Eclipse JDT Core Documentation
- Java Language Specification (JLS) 8 - Lambda Expressions
- JDT ASTParser API Documentation

---

**日期：** 2026-01-16  
**问题发现：** Lambda 中方法调用 `resolveMethodBinding()` 返回 null  
**根本原因：** 缺少 JDT 编译器选项配置  
**解决方案：** 添加 `setCompilerOptions(JavaCore.VERSION_1_8)`  
**验证结果：** 成功识别 lambda 中的方法调用，关系数 +3
