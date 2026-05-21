package com.poseidon.codegraph.parser.javajdt;

import com.poseidon.codegraph.model.CodeGraph;
import com.poseidon.codegraph.model.GraphIds;
import com.poseidon.codegraph.model.RelationshipType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ASTTraverserStructureTest {

    @Test
    void extractsEnumUnitAndTypeRelationships() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        interface Api {
                            String get();
                        }

                        class Base {
                            public String get() {
                                return "base";
                            }
                        }

                        enum Status implements Api {
                            OK;

                            public String get() {
                                return "ok";
                            }
                        }

                        class Child extends Base implements Api {
                            @Override
                            public String get() {
                                return "child";
                            }
                        }
                        """);
        ProcessorContext context = new ProcessorContext();
        context.setCompilationUnit(cu);
        context.setProjectFilePath("src/main/java/com/example/Types.java");
        context.setAbsoluteFilePath("/project/src/main/java/com/example/Types.java");
        context.setProjectName("demo");

        CodeGraph graph = new ASTTraverser(ProcessorRegistry.createCoreOnly().getAll()).traverse(context);

        assertThat(graph.getUnitsAsList())
                .anySatisfy(unit -> {
                    assertThat(unit.getQualifiedName()).isEqualTo("com.example.Status");
                    assertThat(unit.getUnitType()).isEqualTo("enum");
                });
        assertThat(graph.getRelationshipsAsList())
                .anySatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.IMPLEMENTS);
                    assertThat(rel.getFromNodeId()).isEqualTo(GraphIds.unitId("com.example.Status"));
                    assertThat(rel.getToNodeId()).isEqualTo(GraphIds.unitId("com.example.Api"));
                })
                .anySatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.EXTENDS);
                    assertThat(rel.getFromNodeId()).isEqualTo(GraphIds.unitId("com.example.Child"));
                    assertThat(rel.getToNodeId()).isEqualTo(GraphIds.unitId("com.example.Base"));
                })
                .anySatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.OVERRIDES);
                    assertThat(rel.getFromNodeId()).isEqualTo(GraphIds.functionId("com.example.Child.get()"));
                });
    }

    @Test
    void filtersOnlyTrivialBeanAccessorsWhenSourceBodyIsAvailable() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class User {
                            private String name;

                            public String getName() {
                                return name;
                            }

                            public String getDisplayName() {
                                return name.trim();
                            }

                            public String setName(String name) {
                                this.name = name;
                                return name;
                            }
                        }

                        class UseCase {
                            public void run(User user) {
                                user.getName();
                                user.getDisplayName();
                                user.setName("joker");
                            }
                        }
                        """);
        ProcessorContext context = new ProcessorContext();
        context.setCompilationUnit(cu);
        context.setProjectFilePath("src/main/java/com/example/User.java");
        context.setAbsoluteFilePath("/project/src/main/java/com/example/User.java");
        context.setProjectName("demo");

        CodeGraph graph = new ASTTraverser(ProcessorRegistry.createCoreOnly().getAll()).traverse(context);

        assertThat(graph.getRelationshipsAsList())
                .noneSatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                    assertThat(rel.getToNodeId()).isEqualTo(GraphIds.functionId("com.example.User.getName()"));
                })
                .anySatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                    assertThat(rel.getToNodeId()).isEqualTo(GraphIds.functionId("com.example.User.getDisplayName()"));
                })
                .anySatisfy(rel -> {
                    assertThat(rel.getRelationshipType()).isEqualTo(RelationshipType.CALLS);
                    assertThat(rel.getToNodeId()).isEqualTo(GraphIds.functionId("com.example.User.setName(java.lang.String)"));
                });
    }

    @SuppressWarnings("deprecation")
    private static CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Types.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        java.util.Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_17, options);
        parser.setCompilerOptions(options);
        return (CompilationUnit) parser.createAST(null);
    }
}
