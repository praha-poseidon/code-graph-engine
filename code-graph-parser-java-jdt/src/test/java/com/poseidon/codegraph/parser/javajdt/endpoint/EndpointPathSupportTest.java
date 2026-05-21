package com.poseidon.codegraph.parser.javajdt.endpoint;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointPathSupportTest {

    @Test
    void normalizesInboundPathWithoutAstNode() {
        String path = EndpointPathSupport.normalizePathForEndpoint(
            null,
            "https://example.com/api/v1/users/{id}?debug=true#section",
            "inbound");

        assertThat(path).isEqualTo("/api/v{version}/users/{param}");
    }

    @Test
    void resolvesOutboundSimpleNameArgumentFromLocalVariableInitializer() {
        MethodInvocation invocation = firstInvocation("""
            class UserClient {
                void load(RestTemplate restTemplate, String baseUrl, String id) {
                    String url = baseUrl + "/api/v2/users/" + id + "?debug=true";
                    restTemplate.getForObject(url, String.class);
                }
            }
            class RestTemplate {
                void getForObject(String path, Class<?> type) {}
            }
            """);

        String path = EndpointPathSupport.normalizePathForEndpoint(
            invocation,
            "/api/v2/users/{id}?debug=true",
            "outbound");

        assertThat(path).isEqualTo("/api/v{version}/users/{param}");
    }

    @Test
    void fallsBackWhenOutboundNormalizerDropsTooMuchPath() {
        MethodInvocation invocation = firstInvocation("""
            class UserClient {
                void load(RestTemplate restTemplate, String endpoint) {
                    restTemplate.getForObject(endpoint, String.class);
                }
            }
            class RestTemplate {
                void getForObject(String path, Class<?> type) {}
            }
            """);

        String path = EndpointPathSupport.normalizePathForEndpoint(
            invocation,
            "/api/v1/users/{id}",
            "outbound");

        assertThat(path).isEqualTo("/api/v{version}/users/{param}");
    }

    @SuppressWarnings("deprecation")
    private static MethodInvocation firstInvocation(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        MethodDeclaration method = type.getMethods()[0];
        MethodInvocation[] found = new MethodInvocation[1];
        method.accept(new org.eclipse.jdt.core.dom.ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                found[0] = node;
                return false;
            }
        });
        return found[0];
    }
}
