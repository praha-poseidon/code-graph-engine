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
    /** Consumer group (consume side). Not used for MATCHES identity. */
    private String group;
    private String operation;   // PRODUCE, CONSUME
    private String brokerType;  // KAFKA, ROCKETMQ, DDMQ

    public MqEndpoint() {
        setEndpointType(EndpointType.MQ);
    }

    @Override
    public String computeMatchIdentity() {
        // Produce/consume link on topic; group is consumer-side metadata only.
        return "MQ:" + (topic != null ? topic : "UNKNOWN");
    }
}

