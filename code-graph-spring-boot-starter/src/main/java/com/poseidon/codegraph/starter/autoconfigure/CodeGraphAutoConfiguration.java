package com.poseidon.codegraph.starter.autoconfigure;

import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Code Graph runtime services.
 */
@AutoConfiguration
public class CodeGraphAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IncrementalUpdateService incrementalUpdateService(
            CodePackageRepository packageRepository,
            CodeUnitRepository unitRepository,
            CodeFunctionRepository functionRepository,
            CodeRelationshipRepository relationshipRepository,
            CodeEndpointRepository endpointRepository) {
        return new IncrementalUpdateService(
            packageRepository,
            unitRepository,
            functionRepository,
            relationshipRepository,
            endpointRepository);
    }
}
