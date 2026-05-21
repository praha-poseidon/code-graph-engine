package com.poseidon.codegraph.parser.javajdt.endpoint.support;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路径规范化工具
 * 
 * 职责：
 * - 将字符串拼接表达式中的变量替换为 {param}
 * - 识别并跳过 baseUrl
 * - 处理 Spring 路径参数格式（{id}）
 * 
 * 示例：
 * - "/api/users/" + userId → /api/users/{param}
 * - baseUrl + "/api/users/" + id → /api/users/{param}
 * - "/api/users/{id}" → /api/users/{param}
 */
@Slf4j
public class PathNormalizer {
    
    private static final String PARAM_PLACEHOLDER = "{param}";
    
    /**
     * 规范化路径表达式
     * 
     * @param expr 路径表达式（可能是 StringLiteral, InfixExpression 等）
     * @return 规范化后的路径，如果无法规范化则返回 null
     */
    public static String normalizePath(Expression expr) {
        if (expr == null) {
            return null;
        }
        
        if (expr instanceof InfixExpression) {
            // 字符串拼接 → 处理
            return normalizeInfixExpression((InfixExpression) expr);
        } else if (expr instanceof StringLiteral) {
            // 纯字面量 → 规范化 Spring 路径参数格式
            String literalValue = ((StringLiteral) expr).getLiteralValue();
            return normalizeSpringPathParams(literalValue);
        } else {
            // 其他表达式（方法调用等）→ 无法规范化
            log.debug("无法规范化的表达式类型: {}", expr.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * 处理字符串拼接表达式
     */
    private static String normalizeInfixExpression(InfixExpression expr) {
        List<PathSegment> segments = new ArrayList<>();
        collectSegments(expr, segments);
        
        if (segments.isEmpty()) {
            return null;
        }
        
        log.debug("收集到 {} 个路径片段", segments.size());
        for (PathSegment seg : segments) {
            log.debug("  - {}: {}", seg.isLiteral ? "字面量" : "变量", seg.value);
        }
        
        StringBuilder normalized = new StringBuilder();
        boolean skipFirstVariable = true; // 跳过第一个变量（可能是 baseUrl）
        
        for (PathSegment segment : segments) {
            if (segment.isLiteral) {
                // 字面量：直接添加（先处理可能的 URL）
                String value = segment.value;
                
                // 如果是完整 URL，提取路径部分
                value = normalizeCommonPath(extractPathFromUrl(value));
                
                // 规范化 Spring 路径参数
                value = normalizeSpringPathParams(value);
                
                normalized.append(value);
            } else {
                // 变量/表达式
                if (skipFirstVariable && isLikelyBaseUrl(segment.value)) {
                    // 跳过 baseUrl
                    log.debug("跳过 baseUrl: {}", segment.value);
                    skipFirstVariable = false;
                    continue;
                }
                
                // 替换为参数占位符
                normalized.append(PARAM_PLACEHOLDER);
                skipFirstVariable = false;
            }
        }
        
        String result = normalized.toString();
        log.debug("规范化结果: {}", result);
        return result;
    }
    
    /**
     * 递归收集 InfixExpression 中的所有片段
     */
    private static void collectSegments(Expression expr, List<PathSegment> segments) {
        if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression) expr;
            
            // 处理左操作数
            collectSegments(infix.getLeftOperand(), segments);
            
            // 处理右操作数
            collectSegments(infix.getRightOperand(), segments);
            
            // 处理扩展操作数（a + b + c + d 的情况）
            if (infix.hasExtendedOperands()) {
                for (Object extended : infix.extendedOperands()) {
                    collectSegments((Expression) extended, segments);
                }
            }
        } else if (expr instanceof StringLiteral) {
            // 字符串字面量
            String value = ((StringLiteral) expr).getLiteralValue();
            segments.add(new PathSegment(value, true));
        } else if (expr instanceof ParenthesizedExpression) {
            // 括号表达式：递归处理内部表达式
            ParenthesizedExpression paren = (ParenthesizedExpression) expr;
            collectSegments(paren.getExpression(), segments);
        } else {
            // 其他表达式（变量、方法调用、字段访问、三元表达式等）
            // 统一视为变量
            segments.add(new PathSegment(expr.toString(), false));
        }
    }
    
    /**
     * 判断是否可能是 baseUrl
     * 
     * 启发式规则：变量名包含这些关键词 → 很可能是 baseUrl
     */
    private static boolean isLikelyBaseUrl(String variableName) {
        String lower = variableName.toLowerCase();
        return lower.contains("url") || 
               lower.contains("base") || 
               lower.contains("host") || 
               lower.contains("domain") ||
               lower.contains("endpoint") ||
               lower.contains("server");
    }
    
    /**
     * 从 URL 中提取路径部分
     * 
     * http://localhost:8080/api/users → /api/users
     */
    private static String extractPathFromUrl(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        // 匹配 http:// 或 https:// 开头的 URL
        Pattern pattern = Pattern.compile("^https?://[^/]+(.*)$");
        Matcher matcher = pattern.matcher(value);
        
        if (matcher.matches()) {
            String path = normalizeCommonPath(matcher.group(1));
            log.debug("从 URL 提取路径: {} → {}", value, path);
            return path;
        }
        
        return normalizeCommonPath(value);
    }
    
    /**
     * 规范化 Spring 路径参数格式
     * 
     * /api/users/{id} → /api/users/{param}
     * /api/users/{userId}/orders/{orderId} → /api/users/{param}/orders/{param}
     */
    private static String normalizeSpringPathParams(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        
        // 替换所有 {xxx} 为 {param}
        return normalizeCommonPath(path).replaceAll("\\{(?!version\\})[^}]+\\}", PARAM_PLACEHOLDER);
    }

    private static String normalizeCommonPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        return normalizeApiVersion(stripQueryAndFragment(extractUrlPrefix(path)));
    }

    private static String extractUrlPrefix(String path) {
        return path.replaceFirst("^https?://[^/]+", "");
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
    
    /**
     * 路径片段
     */
    private static class PathSegment {
        String value;      // 片段值（字面量的实际值，或变量的名称）
        boolean isLiteral; // 是否是字面量
        
        PathSegment(String value, boolean isLiteral) {
            this.value = value;
            this.isLiteral = isLiteral;
        }
    }
}
