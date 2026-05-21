package com.poseidon.codegraph.model.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息队列端点（Kafka, RocketMQ 等）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MqEndpoint extends CodeEndpoint {
    private String topic;
    private String operation;   // PRODUCE, CONSUME
    private String brokerType;  // KAFKA, ROCKETMQ

    public MqEndpoint() {
        setEndpointType(EndpointType.MQ);
    }

    @Override
    public String computeMatchIdentity() {
        return "MQ:" + (topic != null ? topic : "UNKNOWN");
    }
}

