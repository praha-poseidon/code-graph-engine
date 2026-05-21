package com.poseidon.codegraph.storage.memgraph.repository;

import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeFunctionRepository;
import org.neo4j.driver.Driver;

public class MemgraphCodeFunctionRepository extends Neo4jCodeFunctionRepository {

    public MemgraphCodeFunctionRepository(Driver memgraphDriver) {
        super(memgraphDriver);
    }
}
