package com.poseidon.codegraph.storage.memgraph.repository;

import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeUnitRepository;
import org.neo4j.driver.Driver;

public class MemgraphCodeUnitRepository extends Neo4jCodeUnitRepository {

    public MemgraphCodeUnitRepository(Driver memgraphDriver) {
        super(memgraphDriver);
    }
}
