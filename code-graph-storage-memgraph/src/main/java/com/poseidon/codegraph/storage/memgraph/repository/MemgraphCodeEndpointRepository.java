package com.poseidon.codegraph.storage.memgraph.repository;

import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeEndpointRepository;
import org.neo4j.driver.Driver;

public class MemgraphCodeEndpointRepository extends Neo4jCodeEndpointRepository {

    public MemgraphCodeEndpointRepository(Driver memgraphDriver) {
        super(memgraphDriver);
    }
}
