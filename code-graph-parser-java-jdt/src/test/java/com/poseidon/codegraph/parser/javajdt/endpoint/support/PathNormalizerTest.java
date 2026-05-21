package com.poseidon.codegraph.parser.javajdt.endpoint.support;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathNormalizerTest {

    @Test
    void stripsUrlQueryAndNormalizesPathVariablesAndApiVersion() {
        Expression expression = firstInvocationArgument("""
            class UserClient {
                void load(RestTemplate restTemplate) {
                    restTemplate.getForObject("https://users.example/api/v1/users/{id}?debug=true#top", String.class);
                }
            }
            class RestTemplate {
                void getForObject(String path, Class<?> type) {}
            }
            """);

        assertThat(PathNormalizer.normalizePath(expression))
            .isEqualTo("/api/v{version}/users/{param}");
    }

    @Test
    void normalizesStringConcatenationAndSkipsLikelyBaseUrlVariable() {
        Expression expression = firstInvocationArgument("""
            class UserClient {
                void load(RestTemplate restTemplate, String baseUrl, String id) {
                    restTemplate.getForObject(baseUrl + "/api/v2/users/" + id + "/orders/{orderId}", String.class);
                }
            }
            class RestTemplate {
                void getForObject(String path, Class<?> type) {}
            }
            """);

        assertThat(PathNormalizer.normalizePath(expression))
            .isEqualTo("/api/v{version}/users/{param}/orders/{param}");
    }

    @SuppressWarnings("deprecation")
    private static Expression firstInvocationArgument(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        MethodDeclaration method = type.getMethods()[0];
        final MethodInvocation[] found = new MethodInvocation[1];
        method.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                found[0] = node;
                return false;
            }
        });
        return (Expression) found[0].arguments().get(0);
    }
}
