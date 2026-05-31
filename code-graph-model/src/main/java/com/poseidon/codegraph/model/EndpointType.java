package com.poseidon.codegraph.model;

/**
 * 端点类型枚举
 */
public enum EndpointType {
    /**
     * HTTP 协议端点 (REST API)
     */
    HTTP,

    /**
     * 消息队列端点 (Kafka, RocketMQ 等)
     */
    MQ,

    /**
     * Redis 存储端点
     */
    REDIS,

    /**
     * 数据库存储端点 (MySQL, Oracle 等)
     */
    DB,

    /**
     * UI 操作端点（按钮点击、表单提交等用户入口）
     */
    UI,

    /**
     * 未知类型
     */
    UNKNOWN
}
