package com.poseidon.codegraph.storage.memgraph.repository;

import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodePackageRepository;
import org.neo4j.driver.Driver;

public class MemgraphCodePackageRepository extends Neo4jCodePackageRepository {

    public MemgraphCodePackageRepository(Driver memgraphDriver) {
        super(memgraphDriver);
    }
}
