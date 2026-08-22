package com.poseidon.codegraph.model.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Locale;

/**
 * HTTP 协议端点
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpEndpoint extends CodeEndpoint {
    private String httpMethod;        // GET, POST, PUT, DELETE 等
    private String path;               // /api/users/{id}
    private String normalizedPath;     // 兼容字段；parser 只复制 static-extract 的最终 path

    public HttpEndpoint() {
        setEndpointType(EndpointType.HTTP);
    }

    @Override
    public String computeMatchIdentity() {
        String method = httpMethod == null || httpMethod.isBlank()
                ? "ANY"
                : httpMethod.trim().toUpperCase(Locale.ROOT);
        String identityPath = path != null ? path : (normalizedPath != null ? normalizedPath : "");
        return "HTTP:" + method + ":" + identityPath;
    }
}
