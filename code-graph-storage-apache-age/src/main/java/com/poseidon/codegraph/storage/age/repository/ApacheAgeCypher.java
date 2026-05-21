package com.poseidon.codegraph.storage.age.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ApacheAgeCypher {

    private final JdbcTemplate jdbcTemplate;
    private final String graphName;
    private final boolean initializeGraph;

    public ApacheAgeCypher(
            JdbcTemplate jdbcTemplate,
            @Value("${code-graph.storage.apache-age.graph-name:code_graph}") String graphName,
            @Value("${code-graph.storage.apache-age.initialize-graph:true}") boolean initializeGraph) {
        this.jdbcTemplate = jdbcTemplate;
        this.graphName = graphName;
        this.initializeGraph = initializeGraph;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        setup();
        if (initializeGraph) {
            jdbcTemplate.execute("SELECT create_graph('" + escapeSql(graphName) + "')");
        }
    }

    public List<Map<String, Object>> query(String cypher) {
        setup();
        String sql = "SELECT * FROM cypher('" + escapeSql(graphName) + "', $$ " + cypher + " $$) AS (result agtype)";
        return jdbcTemplate.queryForList(sql);
    }

    public void execute(String cypher) {
        query(cypher);
    }

    public String props(Map<String, ?> values) {
        return values.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> quoteKey(entry.getKey()) + ": " + value(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));
    }

    public String stringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream().map(this::value).collect(Collectors.joining(", ", "[", "]"));
    }

    public String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "'" + escapeCypher(String.valueOf(value)) + "'";
    }

    private void setup() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS age");
        jdbcTemplate.execute("LOAD 'age'");
        jdbcTemplate.execute("SET search_path = ag_catalog, \"$user\", public");
    }

    private String quoteKey(String key) {
        return "`" + key.replace("`", "``") + "`";
    }

    private String escapeCypher(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
