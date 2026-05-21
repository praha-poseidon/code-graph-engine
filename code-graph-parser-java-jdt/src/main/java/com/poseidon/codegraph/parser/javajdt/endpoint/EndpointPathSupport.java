package com.poseidon.codegraph.parser.javajdt.endpoint;

import com.poseidon.codegraph.parser.javajdt.endpoint.support.PathNormalizer;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * Graph-side helpers for endpoint path normalization.
 */
@Slf4j
public final class EndpointPathSupport {

    private EndpointPathSupport() {}

    /** 从绝对文件路径向上找含 src/pom.xml 的根。 */
    public static String resolveProjectRootForTrace(String projectFilePath) {
        if (projectFilePath == null) {
            return null;
        }
        File file = new File(projectFilePath);
        File current = file.getParentFile();
        while (current != null) {
            if (new File(current, "src").exists() || new File(current, "pom.xml").exists()) {
                return current.getAbsolutePath();
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Normalize endpoint paths after static extraction.
     */
    public static String normalizePathForEndpoint(ASTNode node, String path, String direction) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        path = normalizeCommonPath(path);

        if ("inbound".equals(direction)) {
            return normalizeSpringPathParams(path);
        }

        if ("outbound".equals(direction) && node instanceof MethodInvocation) {
            MethodInvocation invocation = (MethodInvocation) node;
            var args = invocation.arguments();

            if (!args.isEmpty() && args.get(0) instanceof Expression pathExprUntyped) {
                Expression pathExpr = pathExprUntyped;

                if (pathExpr instanceof SimpleName) {
                    Expression resolvedExpr = resolveVariableExpression((SimpleName) pathExpr, node);
                    if (resolvedExpr != null) {
                        pathExpr = resolvedExpr;
                        log.debug("追踪到变量定义表达式: {}", pathExpr);
                    }
                }

                String normalized = PathNormalizer.normalizePath(pathExpr);
                if (normalized != null) {
                    boolean lostTooMuch = false;
                    if (normalized.equals("{param}") && path.length() > 7) {
                        lostTooMuch = true;
                    } else if (normalized.startsWith("{param}/")
                            && path.startsWith("/")
                            && !path.startsWith("{param}/")) {
                        lostTooMuch = true;
                    }

                    String pathNormalized = normalizeSpringPathParams(path);
                    if (pathNormalized.length() > normalized.length() && pathNormalized.endsWith(normalized)) {
                        log.debug(
                                "PathNormalizer 跳过了 baseUrl 中的路径片段，回退到原始路径规范化: {} vs {}",
                                normalized,
                                pathNormalized);
                        lostTooMuch = true;
                    }

                    if (lostTooMuch) {
                        log.debug("回退到从解析后的 path 生成规范化路径: {}", pathNormalized);
                        return pathNormalized;
                    }
                    return normalized;
                }
            }
        }

        return normalizeSpringPathParams(path);
    }

    private static Expression resolveVariableExpression(SimpleName varName, ASTNode node) {
        String variableName = varName.getIdentifier();

        ASTNode current = node;
        while (current != null && !(current instanceof MethodDeclaration)) {
            current = current.getParent();
        }

        if (current == null) {
            return null;
        }

        MethodDeclaration method = (MethodDeclaration) current;
        if (method.getBody() == null) {
            return null;
        }

        for (Object stmt : method.getBody().statements()) {
            if (stmt instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement varStmt = (VariableDeclarationStatement) stmt;
                for (Object fragment : varStmt.fragments()) {
                    if (fragment instanceof VariableDeclarationFragment) {
                        VariableDeclarationFragment varFragment = (VariableDeclarationFragment) fragment;
                        if (variableName.equals(varFragment.getName().getIdentifier())) {
                            return varFragment.getInitializer();
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * /{id}/ -> /{param}/
     */
    private static String normalizeSpringPathParams(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return path.replaceAll("\\{(?!version\\})[^}]+\\}", "{param}");
    }

    private static String normalizeCommonPath(String path) {
        return normalizeApiVersion(stripQueryAndFragment(extractPathFromUrl(path)));
    }

    private static String extractPathFromUrl(String value) {
        return value.replaceFirst("^https?://[^/]+", "");
    }

    private static String stripQueryAndFragment(String path) {
        int queryIndex = path.indexOf('?');
        int fragmentIndex = path.indexOf('#');
        int endIndex = -1;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            endIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            endIndex = queryIndex;
        } else if (fragmentIndex >= 0) {
            endIndex = fragmentIndex;
        }
        return endIndex >= 0 ? path.substring(0, endIndex) : path;
    }

    private static String normalizeApiVersion(String path) {
        return path.replaceAll("(?<=/)v\\d+(?:\\.\\d+)*(?=/|$)", "v{version}");
    }
}
