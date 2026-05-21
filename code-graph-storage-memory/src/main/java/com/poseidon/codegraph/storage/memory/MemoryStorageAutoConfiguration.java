package com.poseidon.codegraph.storage.memory;

import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "memory", matchIfMissing = true)
@Import(InMemoryCodeGraphRepository.class)
public class MemoryStorageAutoConfiguration {
}
