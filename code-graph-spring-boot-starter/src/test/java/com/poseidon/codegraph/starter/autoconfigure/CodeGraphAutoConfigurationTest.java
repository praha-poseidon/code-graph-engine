package com.poseidon.codegraph.starter.autoconfigure;

import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import com.poseidon.codegraph.engine.application.repository.CodeFunctionRepository;
import com.poseidon.codegraph.engine.application.repository.CodePackageRepository;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import com.poseidon.codegraph.engine.application.repository.CodeUnitRepository;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.poseidon.codegraph.storage.memory.MemoryStorageAutoConfiguration;
import com.poseidon.codegraph.storage.memory.repository.InMemoryCodeGraphRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGraphAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            MemoryStorageAutoConfiguration.class,
            CodeGraphAutoConfiguration.class));

    @Test
    void defaultsToMemoryStorageWhenStorageTypeIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InMemoryCodeGraphRepository.class);
            assertThat(context).hasSingleBean(CodePackageRepository.class);
            assertThat(context).hasSingleBean(CodeUnitRepository.class);
            assertThat(context).hasSingleBean(CodeFunctionRepository.class);
            assertThat(context).hasSingleBean(CodeEndpointRepository.class);
            assertThat(context).hasSingleBean(CodeRelationshipRepository.class);
            assertThat(context).hasSingleBean(IncrementalUpdateService.class);
        });
    }

    @Test
    void rejectsUnknownStorageTypeBecauseNoStorageAdapterMatches() {
        contextRunner
            .withPropertyValues("code-graph.storage.type=unknown")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("CodePackageRepository");
            });
    }
}
