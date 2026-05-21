package com.poseidon.codegraph.parser.javajdt.endpoint.mapper;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.CodeFunction;
import com.poseidon.codegraph.model.EndpointType;
import com.poseidon.codegraph.model.endpoint.DbEndpoint;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import com.poseidon.codegraph.model.endpoint.MqEndpoint;
import com.poseidon.codegraph.model.endpoint.RedisEndpoint;
import com.poseidon.codegraph.parser.javajdt.JdtGraphIds;
import com.poseidon.codegraph.parser.javajdt.endpoint.EndpointPathSupport;
import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.rule.EndpointSpec;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * 将静态提取产物映射为图谱端点。
 *
 * <p>static-extract 只产出 endpoint label 和字段 Map。这里是图谱层边界：
 * endpoint type 选择具体图谱类，build 字段按同名 setter 写入类属性。
 */
@Slf4j
public final class StaticExtractEndpointMapper {

    private StaticExtractEndpointMapper() {}

    public static CodeEndpoint toCodeEndpoint(
            StaticExtractResult result, CompilationUnit cu, TypeDeclaration typeDecl, String projectFilePath) {
        if (result == null || result.rule() == null || result.fields() == null) {
            return null;
        }
        StaticExtractRule rule = result.rule();
        EndpointSpec endpointSpec = rule.endpoint();
        CodeEndpoint endpoint = createEndpoint(endpointSpec != null ? endpointSpec.type() : null);
        if (endpoint == null) {
            log.debug("图谱端点模型暂不支持 endpoint type: {}", endpointSpec != null ? endpointSpec.type() : null);
            return null;
        }

        String direction = endpointSpec != null ? endpointSpec.direction() : null;
        endpoint.setDirection(direction);
        endpoint.setIsExternal("outbound".equalsIgnoreCase(direction));
        endpoint.setProjectFilePath(projectFilePath);
        endpoint.setLanguage("java");
        endpoint.setParseLevel("full");

        ASTNode anchor = result.anchorNode();
        String functionId = extractFunctionId(anchor, cu, typeDecl);
        if (functionId != null) {
            CodeFunction f = new CodeFunction();
            f.setId(functionId);
            endpoint.setFunction(f);
        }
        int line =
                anchor != null && cu != null ? cu.getLineNumber(anchor.getStartPosition())
                        : result.startLine();
        int lineEnd =
                anchor != null && cu != null
                        ? cu.getLineNumber(
                                anchor.getStartPosition()
                                        + Math.max(anchor.getLength() - 1, 0))
                        : result.endLine();
        endpoint.setStartLine(line);
        endpoint.setEndLine(lineEnd);

        for (Map.Entry<String, String> entry : result.fields().entrySet()) {
            applyField(endpoint, entry.getKey(), entry.getValue());
        }

        completeDerivedFields(endpoint, anchor);

        endpoint.setMatchIdentity(endpoint.computeMatchIdentity());
        endpoint.setName(endpoint.getMatchIdentity());
        endpoint.setQualifiedName(endpoint.getMatchIdentity());
        String id = generateEndpointId(endpoint);
        if (id == null) {
            return null;
        }
        endpoint.setId(id);

        return endpoint;
    }

    private static CodeEndpoint createEndpoint(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            EndpointType endpointType = EndpointType.valueOf(type.toUpperCase(Locale.ROOT));
            return switch (endpointType) {
                case HTTP -> new HttpEndpoint();
                case MQ -> new MqEndpoint();
                case REDIS -> new RedisEndpoint();
                case DB -> new DbEndpoint();
                case UNKNOWN -> null;
            };
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void applyField(CodeEndpoint endpoint, String field, String value) {
        if (field == null || field.isBlank() || value == null) {
            return;
        }
        String setterName = "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (Method method : endpoint.getClass().getMethods()) {
            if (!method.getName().equals(setterName) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            try {
                if (String.class.equals(parameterType)) {
                    method.invoke(endpoint, value);
                    return;
                }
                if (Boolean.class.equals(parameterType) || boolean.class.equals(parameterType)) {
                    method.invoke(endpoint, Boolean.valueOf(value));
                    return;
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to apply endpoint field: " + field, e);
            }
        }
        log.debug("端点字段没有匹配的 setter，已忽略: {}.{}", endpoint.getClass().getSimpleName(), field);
    }

    private static void completeDerivedFields(CodeEndpoint endpoint, ASTNode anchor) {
        if (endpoint instanceof HttpEndpoint http) {
            if (http.getHttpMethod() == null || http.getHttpMethod().isBlank()) {
                http.setHttpMethod("UNKNOWN");
            }
            if (http.getPath() == null || http.getPath().isBlank()) {
                return;
            }
            String normalizedPath =
                    EndpointPathSupport.normalizePathForEndpoint(
                            anchor, http.getPath(), http.getDirection());
            http.setPath(normalizedPath);
            http.setNormalizedPath(normalizedPath);
        }
    }

    private static String generateEndpointId(CodeEndpoint endpoint) {
        String type = endpoint.getEndpointType() != null ? endpoint.getEndpointType().name() : "UNKNOWN";
        if (endpoint instanceof HttpEndpoint http) {
            if (http.getPath() == null || http.getPath().isBlank()) {
                return null;
            }
            return com.poseidon.codegraph.model.GraphIds.endpointId(
                    endpoint.getDirection(),
                    type,
                    endpoint.computeMatchIdentity());
        }
        return com.poseidon.codegraph.model.GraphIds.endpointId(
                endpoint.getDirection(),
                type,
                endpoint.computeMatchIdentity());
    }

    private static String extractFunctionId(ASTNode anchor, CompilationUnit cu, TypeDeclaration td) {
        MethodDeclaration enclosing = null;
        if (anchor instanceof MethodDeclaration md) {
            enclosing = md;
        } else if (anchor instanceof MethodInvocation) {
            ASTNode p = anchor.getParent();
            while (p != null && !(p instanceof MethodDeclaration)) {
                p = p.getParent();
            }
            if (p instanceof MethodDeclaration md2) {
                enclosing = md2;
            }
        }
        if (enclosing == null) {
            return null;
        }
        IMethodBinding binding = enclosing.resolveBinding();
        if (binding == null || binding.getDeclaringClass() == null) {
            String pkg =
                    cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() + "." : "";
            return JdtGraphIds.functionId(pkg + td.getName().getIdentifier() + "." + enclosing.getName().getIdentifier());
        }
        return JdtGraphIds.functionId(JdtGraphIds.qualifiedMethodSignature(binding));
    }

}
