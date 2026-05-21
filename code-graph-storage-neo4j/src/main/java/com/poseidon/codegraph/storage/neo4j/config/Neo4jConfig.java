package com.poseidon.codegraph.storage.neo4j.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 配置类
 */
@Configuration
@ConditionalOnProperty(name = "code-graph.storage.type", havingValue = "neo4j")
public class Neo4jConfig {
    
    @Value("${spring.neo4j.uri:bolt://localhost:7687}")
    private String uri;
    
    @Value("${spring.neo4j.authentication.username:neo4j}")
    private String username;
    
    @Value("${spring.neo4j.authentication.password:password}")
    private String password;
    
    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
