package com.poseidon.codegraph.storage.neo4j;

import com.poseidon.codegraph.storage.neo4j.config.Neo4jConfig;
import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeEndpointRepository;
import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeFunctionRepository;
import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodePackageRepository;
import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeRelationshipRepository;
import com.poseidon.codegraph.storage.neo4j.repository.Neo4jCodeUnitRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
@Import({
    Neo4jConfig.class,
    Neo4jCodePackageRepository.class,
    Neo4jCodeUnitRepository.class,
    Neo4jCodeFunctionRepository.class,
    Neo4jCodeEndpointRepository.class,
    Neo4jCodeRelationshipRepository.class
})
public class Neo4jStorageAutoConfiguration {
}
