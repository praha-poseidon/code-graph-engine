package com.poseidon.codegraph.storage.memgraph;

import com.poseidon.codegraph.storage.memgraph.config.MemgraphConfig;
import com.poseidon.codegraph.storage.memgraph.repository.MemgraphCodeEndpointRepository;
import com.poseidon.codegraph.storage.memgraph.repository.MemgraphCodeFunctionRepository;
import com.poseidon.codegraph.storage.memgraph.repository.MemgraphCodePackageRepository;
import com.poseidon.codegraph.storage.memgraph.repository.MemgraphCodeRelationshipRepository;
import com.poseidon.codegraph.storage.memgraph.repository.MemgraphCodeUnitRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "memgraph")
@Import({
    MemgraphConfig.class,
    MemgraphCodePackageRepository.class,
    MemgraphCodeUnitRepository.class,
    MemgraphCodeFunctionRepository.class,
    MemgraphCodeEndpointRepository.class,
    MemgraphCodeRelationshipRepository.class
})
public class MemgraphStorageAutoConfiguration {
}
