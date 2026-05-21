package com.poseidon.codegraph.storage.memgraph.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemgraphConfig {

    @Value("${code-graph.storage.memgraph.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${code-graph.storage.memgraph.username:}")
    private String username;

    @Value("${code-graph.storage.memgraph.password:}")
    private String password;

    @Bean
    public Driver memgraphDriver() {
        if (username == null || username.isBlank()) {
            return GraphDatabase.driver(uri, AuthTokens.none());
        }
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password != null ? password : ""));
    }
}
