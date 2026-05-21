package com.poseidon.codegraph.model.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Redis 端点
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RedisEndpoint extends CodeEndpoint {
    private String keyPattern;
    private String command;       // GET, SET, DEL 等
    private String dataStructure; // STRING, HASH, LIST 等

    public RedisEndpoint() {
        setEndpointType(EndpointType.REDIS);
    }

    @Override
    public String computeMatchIdentity() {
        return "REDIS:" + (keyPattern != null ? keyPattern : "UNKNOWN");
    }
}

