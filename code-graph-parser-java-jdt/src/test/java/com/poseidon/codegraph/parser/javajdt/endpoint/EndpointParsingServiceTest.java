package com.poseidon.codegraph.parser.javajdt.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.endpoint.HttpEndpoint;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EndpointParsingServiceTest {

    @Test
    void parsesSpringMvcEndpointThroughApplicationSerRules() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api")
                        class UserController {
                            @GetMapping("/users/{id}")
                            public String getUser(String id) {
                                return id;
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        EndpointParsingService service = new EndpointParsingService();
        service.init();

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "UserController.java",
                        "src/main/java/com/example/UserController.java",
                        null);

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("inbound", endpoint.getDirection());
        assertEquals("GET", endpoint.getHttpMethod());
        assertEquals("/api/users/{param}", endpoint.getPath());
        assertEquals("GET /api/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void resolvesOutboundPathFromSpringValueTraceAndExternalValues() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        import org.springframework.beans.factory.annotation.Value;
                        class RestTemplate {
                            String getForObject(String url, Class<?> responseType) {
                                return null;
                            }
                        }

                        class UserClient {
                            @Value("${users.base-url:http://fallback}")
                            private String baseUrl;

                            private final RestTemplate restTemplate = new RestTemplate();

                            public String load(String id) {
                                return restTemplate.getForObject(baseUrl + "/users/" + id, String.class);
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(1);
        EndpointParsingService service = new EndpointParsingService();
        service.init();

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "UserClient.java",
                        "src/main/java/com/example/UserClient.java",
                        null,
                        Map.of("config", Map.of("users.base-url", List.of("http://users.example/api"))));

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("outbound", endpoint.getDirection());
        assertEquals("GET", endpoint.getHttpMethod());
        assertEquals("/api/users/{param}", endpoint.getPath());
        assertEquals("GET /api/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void acceptsExternalSerRuleSourcesAsStrings() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        import org.springframework.web.bind.annotation.GetMapping;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping("/api")
                        class UserController {
                            @GetMapping("/users/{id}")
                            public String getUser(String id) {
                                return id;
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        EndpointParsingService service =
                new EndpointParsingService(
                        List.of(
                                """
                                rule "External Spring MVC"
                                endpoint HTTP inbound

                                find method with annotation @*Mapping

                                let basePath =
                                  from annotation on class @RequestMapping take attr(value)
                                  default ""

                                let methodPath =
                                  from annotation on method @*Mapping take attr(value)
                                  default ""

                                let httpMethod =
                                  from annotation on method @*Mapping take name
                                  map {
                                    GetMapping: GET
                                  }

                                build {
                                  httpMethod: httpMethod
                                  path: concat(basePath, methodPath) | normalize slash | normalize pathVariable
                                }
                                """),
                        List.of(),
                        false);

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "UserController.java",
                        "src/main/java/com/example/UserController.java",
                        null);

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("GET /api/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void acceptsUnifiedExternalSerSourceWithRuleAndTraceBlocks() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        import org.springframework.beans.factory.annotation.Value;
                        class RestTemplate {
                            String getForObject(String url, Class<?> responseType) {
                                return null;
                            }
                        }

                        class UserClient {
                            @Value("${users.base-url:http://fallback}")
                            private String baseUrl;

                            private final RestTemplate restTemplate = new RestTemplate();

                            public String load(String id) {
                                return restTemplate.getForObject(baseUrl + "/users/" + id, String.class);
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(1);
        EndpointParsingService service =
                new EndpointParsingService(
                        List.of(
                                """
                                rule "External RestTemplate"
                                endpoint HTTP outbound

                                find method RestTemplate.getForObject

                                let rawUrl =
                                  from argument[0] take value

                                let httpMethod =
                                  from method take name
                                  map {
                                    getForObject: GET
                                  }

                                build {
                                  httpMethod: httpMethod
                                  path: rawUrl | normalize extractPath | normalize pathVariable
                                }

                                trace "External Config Trace"

                                from field
                                when annotation @Value on field

                                let rawValue =
                                  from annotation on field @Value take attr(value)

                                build {
                                  namespace: "config"
                                  lookup: rawValue | normalize placeholderLookup
                                  default: rawValue | normalize placeholderDefault
                                }
                                """),
                        List.of(),
                        false);

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "UserClient.java",
                        "src/main/java/com/example/UserClient.java",
                        null,
                        Map.of("config", Map.of("users.base-url", List.of("http://users.example/api/v1"))));

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("GET", endpoint.getHttpMethod());
        assertEquals("/api/v{version}/users/{param}", endpoint.getPath());
        assertEquals("GET /api/v{version}/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void parsesClassAndMethodPathAttributesAndStripsQueryParameters() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        import org.springframework.web.bind.annotation.PostMapping;
                        import org.springframework.web.bind.annotation.RequestMapping;
                        import org.springframework.web.bind.annotation.RestController;

                        @RestController
                        @RequestMapping(path = "/api")
                        class OrderController {
                            @PostMapping(path = "/orders/{orderId}?debug=true")
                            public String create(String orderId) {
                                return orderId;
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        EndpointParsingService service = new EndpointParsingService();
        service.init();

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "OrderController.java",
                        "src/main/java/com/example/OrderController.java",
                        null);

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("POST", endpoint.getHttpMethod());
        assertEquals("/api/orders/{param}", endpoint.getPath());
        assertEquals("POST /api/orders/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void reportsEndpointRuleSourceIndexWhenExternalSerIsInvalid() {
        assertThatThrownBy(() -> new EndpointParsingService(List.of("broken rule"), List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid endpoint SER rule source at index 0")
                .hasMessageContaining("Invalid SER syntax");
    }

    @Test
    void reportsTraceRuleSourceIndexWhenExternalTraceSerIsInvalid() {
        assertThatThrownBy(() -> new EndpointParsingService(List.of(), List.of("broken trace"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trace SER rule source at index 0")
                .hasMessageContaining("Invalid SER syntax");
    }

    @Test
    void stripsQueryAndNormalizesApiVersionFromOutboundPath() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class RestTemplate {
                            String getForObject(String url, Class<?> responseType) {
                                return null;
                            }
                        }

                        class UserClient {
                            private final RestTemplate restTemplate = new RestTemplate();

                            public String load() {
                                return restTemplate.getForObject("http://users.example/api/v1/users?id=1", String.class);
                            }
                        }
                        """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(1);
        EndpointParsingService service = new EndpointParsingService();
        service.init();

        List<CodeEndpoint> endpoints =
                service.parseEndpointsForType(
                        type,
                        cu,
                        "com.example",
                        "UserClient.java",
                        "src/main/java/com/example/UserClient.java",
                        null);

        assertEquals(1, endpoints.size());
        HttpEndpoint endpoint = assertInstanceOf(HttpEndpoint.class, endpoints.get(0));
        assertEquals("/api/v{version}/users", endpoint.getPath());
        assertEquals("GET /api/v{version}/users", endpoint.getMatchIdentity());
    }

    @SuppressWarnings("deprecation")
    private static CompilationUnit parse(String source) {
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
