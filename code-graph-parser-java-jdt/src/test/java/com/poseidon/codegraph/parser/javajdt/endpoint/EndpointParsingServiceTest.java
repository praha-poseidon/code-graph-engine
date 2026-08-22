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

    private static final String SPRING_MVC = """
            rule "Spring MVC HTTP Inbound"
            endpoint HTTP inbound

            find method
            when annotation @*Mapping on method

            let basePath =
              from annotation @RequestMapping on class take attr(value)
              from annotation @RequestMapping on class take attr(path)
              fallback ""
            let methodPath =
              from annotation @*Mapping on method take attr(value)
              from annotation @*Mapping on method take attr(path)
              fallback ""
            let httpMethod =
              from annotation @*Mapping on method take name
              map {
                GetMapping: GET
                PostMapping: POST
                PutMapping: PUT
                DeleteMapping: DELETE
                PatchMapping: PATCH
                RequestMapping: GET
              }

            build {
              httpMethod: httpMethod
              path: concat(basePath, methodPath) | normalize slash | normalize pathVariable
            }
            """;

    private static final String REST_TEMPLATE = """
            rule "RestTemplate HTTP Outbound"
            endpoint HTTP outbound

            find call RestTemplate.[getForObject,getForEntity,postForObject,postForEntity,put,delete]

            let rawUrl =
              from argument[0] take value

            let httpMethod =
              from method take name
              map {
                getForObject: GET
                getForEntity: GET
                postForObject: POST
                postForEntity: POST
                put: PUT
                delete: DELETE
              }

            build {
              httpMethod: httpMethod
              path: rawUrl | normalize extractPath | normalize pathVariable
            }

            trace {
              from field
              when annotation @Value on field

              let rawValue =
                from annotation @Value on field take attr(value)

              build {
                namespace: "config"
                lookup: rawValue | normalize placeholderLookup
                default: rawValue | normalize placeholderDefault
              }

              from call
              when method Environment.getProperty

              let configLookup =
                from argument[0] take value

              build {
                namespace: "config"
                lookup: configLookup
              }
            }
            """;

    @Test
    void parsesSpringMvcEndpointThroughCallerSerRules() {
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
        EndpointParsingService service = new EndpointParsingService(List.of(SPRING_MVC), List.of());

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
        assertEquals("HTTP:GET:/api/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void resolvesOutboundPathFromEmbeddedTraceAndExternalValues() {
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
        EndpointParsingService service = new EndpointParsingService(List.of(REST_TEMPLATE), List.of());

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
        assertEquals("HTTP:GET:/api/users/{param}", endpoint.getMatchIdentity());
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
        EndpointParsingService service = new EndpointParsingService(List.of(SPRING_MVC), List.of());

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
        assertEquals("HTTP:GET:/api/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void acceptsUnifiedSerSourceWithEmbeddedTraceBlock() {
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
        EndpointParsingService service = new EndpointParsingService(List.of(REST_TEMPLATE), List.of());

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
        assertEquals("/api/v1/users/{param}", endpoint.getPath());
        assertEquals("HTTP:GET:/api/v1/users/{param}", endpoint.getMatchIdentity());
    }

    @Test
    void parsesClassAndMethodPathAttributesWithoutParserSidePathRewrites() {
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
        EndpointParsingService service = new EndpointParsingService(List.of(SPRING_MVC), List.of());

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
        assertEquals("/api/orders/{param}?debug=true", endpoint.getPath());
        assertEquals("HTTP:POST:/api/orders/{param}?debug=true", endpoint.getMatchIdentity());
    }

    @Test
    void reportsEndpointRuleSourceIndexWhenExternalSerIsInvalid() {
        assertThatThrownBy(() -> new EndpointParsingService(List.of("broken rule"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid endpoint SER rule source at index 0");
    }

    @Test
    void emptyRulesYieldNoEndpoints() {
        EndpointParsingService service = new EndpointParsingService(List.of(), List.of());
        service.init();
        CompilationUnit cu = parse("package com.example; class A {}");
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        assertEquals(
                List.of(),
                service.parseEndpointsForType(type, cu, "com.example", "A.java", "A.java", null));
    }

    @Test
    void serExtractPathStripsQueryButKeepsLiteralApiVersion() {
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
        EndpointParsingService service = new EndpointParsingService(List.of(REST_TEMPLATE), List.of());

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
        assertEquals("/api/v1/users", endpoint.getPath());
        assertEquals("HTTP:GET:/api/v1/users", endpoint.getMatchIdentity());
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
