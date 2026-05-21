package com.poseidon.codegraph.storage.age.repository;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApacheAgeCodeGraphRepositoryTest {

    @Test
    void mergesRelationshipsWithProjectScopedEndpoints() {
        CapturingAgeCypher age = new CapturingAgeCypher();
        ApacheAgeCodeGraphRepository repository = new ApacheAgeCodeGraphRepository(age);

        repository.insertRelationshipsBatch(List.of(relationship()));

        assertThat(age.executed())
            .singleElement()
            .satisfies(cypher -> assertThat(cypher)
                .contains("MATCH (from:CodeUnit {id: 'unit', projectName: 'demo'})")
                .contains("MATCH (to:CodeFunction {id: 'function', projectName: 'demo'})")
                .contains("MERGE (from)-[r:UNIT_TO_FUNCTION {id: 'rel'}]->(to)")
                .contains("projectName: 'demo'"));
    }

    @Test
    void existingStructureRelationshipQueryIsProjectScoped() {
        CapturingAgeCypher age = new CapturingAgeCypher();
        ApacheAgeCodeGraphRepository repository = new ApacheAgeCodeGraphRepository(age);

        repository.findExistingStructureRelationships("demo", List.of(relationship()));

        assertThat(age.queried())
            .singleElement()
            .satisfies(cypher -> assertThat(cypher)
                .contains("MATCH (from {id: 'unit'})-[r:UNIT_TO_FUNCTION]->(to {id: 'function'})")
                .contains("from.projectName = 'demo'")
                .contains("to.projectName = 'demo'")
                .contains("r.projectName = 'demo'"));
    }

    private CodeRelationshipDO relationship() {
        CodeRelationshipDO relationship = new CodeRelationshipDO();
        relationship.setId("rel");
        relationship.setFromNodeId("unit");
        relationship.setToNodeId("function");
        relationship.setRelationshipType("UNIT_TO_FUNCTION");
        relationship.setProjectName("demo");
        relationship.setLanguage("java");
        return relationship;
    }

    private static class CapturingAgeCypher extends ApacheAgeCypher {
        private final List<String> executed = new ArrayList<>();
        private final List<String> queried = new ArrayList<>();

        CapturingAgeCypher() {
            super(null, "code_graph", false);
        }

        @Override
        public List<Map<String, Object>> query(String cypher) {
            queried.add(cypher);
            return List.of();
        }

        @Override
        public void execute(String cypher) {
            executed.add(cypher);
        }

        List<String> executed() {
            return executed;
        }

        List<String> queried() {
            return queried;
        }
    }
}
