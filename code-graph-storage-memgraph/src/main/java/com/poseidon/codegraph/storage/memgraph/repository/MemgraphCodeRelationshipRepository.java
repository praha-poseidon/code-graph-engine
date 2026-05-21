package com.poseidon.codegraph.storage.memgraph.repository;

import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeRelationshipRepository;
import org.neo4j.driver.Driver;

public class MemgraphCodeRelationshipRepository extends Neo4jCodeRelationshipRepository {

    public MemgraphCodeRelationshipRepository(Driver memgraphDriver) {
        super(memgraphDriver);
    }
}
