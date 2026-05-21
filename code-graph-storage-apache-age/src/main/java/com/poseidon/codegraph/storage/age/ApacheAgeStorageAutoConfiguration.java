package com.poseidon.codegraph.storage.age;

import com.poseidon.codegraph.storage.age.repository.ApacheAgeCodeGraphRepository;
import com.poseidon.codegraph.storage.age.repository.ApacheAgeCypher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "apache-age")
@Import({ApacheAgeCypher.class, ApacheAgeCodeGraphRepository.class})
public class ApacheAgeStorageAutoConfiguration {
}
