package com.poseidon.codegraph.model.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * HTTP 协议端点
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpEndpoint extends CodeEndpoint {
    private String httpMethod;        // GET, POST, PUT, DELETE 等
    private String path;               // /api/users/{id}
    private String normalizedPath;     // 标准化后的路径（包含方法名）

    public HttpEndpoint() {
        setEndpointType(EndpointType.HTTP);
    }

    @Override
    public String computeMatchIdentity() {
        return (httpMethod != null ? httpMethod : "UNKNOWN") + " " + 
               (normalizedPath != null ? normalizedPath : (path != null ? path : ""));
    }
}

