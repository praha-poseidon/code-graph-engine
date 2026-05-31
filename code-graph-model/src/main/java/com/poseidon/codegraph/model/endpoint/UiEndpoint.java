package com.poseidon.codegraph.model.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * UI 操作端点，表示用户从界面进入系统的入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UiEndpoint extends CodeEndpoint {
    private String uiEvent;       // click, submit, change 等
    private String uiElement;     // button, form, link 等
    private String uiText;        // 用户可见文案
    private String uiSelector;    // 可选选择器或组件标识
    private String routePath;     // 页面路由，可选
    private String componentName; // 所属组件，可选

    public UiEndpoint() {
        setEndpointType(EndpointType.UI);
    }

    @Override
    public String computeMatchIdentity() {
        String event = uiEvent != null ? uiEvent.toUpperCase() : "UNKNOWN";
        String element = uiElement != null ? uiElement : "unknown";
        String text = uiText != null && !uiText.isBlank() ? uiText : element;
        return "UI:" + event + ":" + element + ":" + text;
    }
}
