package com.poseidon.codegraph.storage.neo4j.repository;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Neo4jCodeRelationshipRepositoryTest {

    @Test
    void mergesRelationshipsByRelationshipIdAndStoresProjectName() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(mock(Result.class));
        Neo4jCodeRelationshipRepository repository = new Neo4jCodeRelationshipRepository(driver);

        repository.insertRelationshipsBatch(List.of(relationship()));

        org.mockito.ArgumentCaptor<String> cypher = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(session).run(cypher.capture(), any(Value.class));
        assertThat(cypher.getValue())
            .contains("MATCH (from:CodeUnit {id: rel.fromNodeId})")
            .contains("MATCH (to:CodeFunction {id: rel.toNodeId})")
            .contains("MERGE (from)-[r:UNIT_TO_FUNCTION {id: rel.id}]->(to)")
            .contains("r.projectName = rel.projectName");
    }

    @Test
    void outgoingRelationshipQueryIsProjectScoped() {
        Driver driver = mock(Driver.class);
        Session session = mock(Session.class);
        Result result = mock(Result.class);
        when(driver.session()).thenReturn(session);
        when(session.run(anyString(), any(Value.class))).thenReturn(result);
        when(result.stream()).thenReturn(java.util.stream.Stream.empty());
        Neo4jCodeRelationshipRepository repository = new Neo4jCodeRelationshipRepository(driver);

        repository.findOutgoingRelationships("demo", "node", "CALLS");

        org.mockito.ArgumentCaptor<String> cypher = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(session).run(cypher.capture(), any(Value.class));
        assertThat(cypher.getValue())
            .contains("MATCH (from {projectName: $projectName, id: $nodeId})-[r]->()")
            .contains("WHERE r.projectName = $projectName")
            .contains("type(r) = $relationshipType");
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
}
