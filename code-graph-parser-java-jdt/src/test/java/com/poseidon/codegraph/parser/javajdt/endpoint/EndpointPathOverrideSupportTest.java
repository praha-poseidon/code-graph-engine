package com.poseidon.codegraph.parser.javajdt.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import com.poseidon.codegraph.model.endpoint.MqEndpoint;
import com.poseidon.javastatic.extract.jdt.external.EndpointIdentityOverride;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identity dictionary is applied inside static-extract ({@link EndpointIdentityOverride}).
 * code-graph only maps extract fields → CodeEndpoint.
 */
class EndpointPathOverrideSupportTest {

    @Test
    void methodKeyHasNoSiteTag() {
        assertEquals(
                "com.example.KaleidoGatewayImpl.a()",
                EndpointIdentityOverride.methodKey("com.example.KaleidoGatewayImpl", "a", 0));
        assertEquals(
                "com.example.CooperAssetConsumer.run()",
                EndpointIdentityOverride.methodKey("com.example.CooperAssetConsumer", "run", 0));
    }

    @Test
    void overridesOutboundPathFromUserDictionaryKey() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class WebClient {
                          static class Builder {
                            WebClient build() { return new WebClient(); }
                          }
                          WebClient get() { return this; }
                          WebClient post() { return this; }
                          WebClient uri(String u) { return this; }
                          void retrieve() {}
                        }

                        class KaleidoGatewayImpl {
                          private WebClient.Builder b;

                          public void a() {
                            b.build().get().uri(buildRecentlyUsedTemplateUrl()).retrieve();
                          }

                          private String buildRecentlyUsedTemplateUrl() {
                            return "should-not-appear";
                          }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "KaleidoGatewayImpl");

        String webClientSer =
                """
                rule "WebClient HTTP Outbound"
                endpoint HTTP outbound

                find call WebClient.[get,post]

                let httpMethod =
                  from call take name
                  map {
                    get: GET
                    post: POST
                  }

                let path =
                  from chain next uri argument[0] take value

                build {
                  httpMethod: httpMethod
                  path: path
                }
                dict {
                  com.example.KaleidoGatewayImpl.a() = v1/bac/sdfsdf
                }
                """;

        EndpointParsingService service = new EndpointParsingService(List.of(webClientSer), List.of());

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "KaleidoGatewayImpl.java",
                        "src/main/java/com/example/KaleidoGatewayImpl.java",
                        null);

        assertEquals(1, endpoints.size(), "one outbound endpoint");
        HttpEndpoint ep = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("outbound", ep.getDirection());
        assertEquals("GET", ep.getHttpMethod());
        assertEquals("/v1/bac/sdfsdf", ep.getPath());
        assertEquals("config", ep.getParseLevel());
        assertFalse(ep.getPath().contains("buildRecentlyUsed"), "must not keep method call as path");
    }

    @Test
    void withoutDictionaryKeepsSerExtractedPath() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class WebClient {
                          static class Builder { WebClient build() { return new WebClient(); } }
                          WebClient get() { return this; }
                          WebClient uri(String u) { return this; }
                          void retrieve() {}
                        }

                        class Client {
                          private WebClient.Builder b;
                          void load() {
                            b.build().get().uri("/api/literal").retrieve();
                          }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Client");
        String webClientSer =
                """
                rule "WebClient HTTP Outbound"
                endpoint HTTP outbound
                find call WebClient.[get,post]
                let httpMethod = from call take name map { get: GET post: POST }
                let path = from chain next uri argument[0] take value
                build { httpMethod: httpMethod path: path }
                """;
        EndpointParsingService service = new EndpointParsingService(List.of(webClientSer), List.of());
        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type, cu, "com.example", "Client.java", "Client.java", null);
        assertEquals(1, endpoints.size());
        HttpEndpoint ep = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertTrue(
                ep.getPath() != null && ep.getPath().contains("/api/literal"),
                "without dict keep SER path, got: " + ep.getPath());
    }

    @Test
    void overridesMqTopicViaStaticExtractDict() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;
                        class CarreraProducer {
                          Object send(String topic, String body) { return null; }
                        }
                        class Producer {
                          CarreraProducer carreraProducer;
                          String topic;
                          public void sendEvent() {
                            carreraProducer.send(topic, "body");
                          }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Producer");
        String mqSer =
                """
                rule "Carrera send"
                endpoint MQ outbound
                find call CarreraProducer.[send]
                let topic = from argument[0] take value
                build { topic: topic }
                dict {
                  com.example.Producer.sendEvent() = order.created
                }
                """;
        EndpointParsingService service = new EndpointParsingService(List.of(mqSer), List.of());

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type, cu, "com.example", "Producer.java", "Producer.java", null);

        assertEquals(1, endpoints.size());
        MqEndpoint mq = assertInstanceOf(MqEndpoint.class, endpoints.get(0));
        assertEquals("order.created", mq.getTopic());
        assertEquals("config", mq.getParseLevel());
    }

    private TypeDeclaration typeNamed(CompilationUnit cu, String name) {
        for (Object t : cu.types()) {
            if (t instanceof TypeDeclaration td && name.equals(td.getName().getIdentifier())) {
                return td;
            }
        }
        throw new IllegalStateException("type not found: " + name);
    }

    @SuppressWarnings("deprecation")
    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Test.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(JavaCore.getOptions());
        return (CompilationUnit) parser.createAST(null);
    }
}
