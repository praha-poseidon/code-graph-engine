package com.poseidon.codegraph.app.adapter.controller;

import com.poseidon.codegraph.app.adapter.dto.ApiResponse;
import com.poseidon.codegraph.app.adapter.dto.CreateFileNodesRequest;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CodeGraphControllerTest {

    private final IncrementalUpdateService service = mock(IncrementalUpdateService.class);
    private final CodeGraphController controller = new CodeGraphController(service, new ObjectMapper());

    @Test
    void healthReturnsOk() {
        ApiResponse<String> response = controller.health();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("服务运行正常");
        assertThat(response.getData()).isEqualTo("OK");
    }

    @Test
    void updateRejectsMissingAbsoluteFilePath() {
        CreateFileNodesRequest request = request();
        request.setAbsoluteFilePath(" ");

        ApiResponse<Void> response = controller.updateFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("文件绝对路径不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void updateRejectsMissingProjectName() {
        CreateFileNodesRequest request = request();
        request.setProjectName(" ");

        ApiResponse<Void> response = controller.updateFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("项目名称不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void updateRejectsMissingProjectFilePath() {
        CreateFileNodesRequest request = request();
        request.setProjectFilePath(null);

        ApiResponse<Void> response = controller.updateFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("项目相对路径不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void updateDelegatesToServiceAndConvertsPathLists() {
        CreateFileNodesRequest request = request();

        ApiResponse<Void> response = controller.updateFileNodes(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("文件节点更新成功");
        verify(service).handleFileModified(
            eq("demo"),
            eq("/repo/src/App.java"),
            eq("src/App.java"),
            eq("git@example/demo.git"),
            eq("main"),
            eq(new String[] {"classes"}),
            eq(new String[] {"src/main/java"}),
            eq(List.of("endpoint rule")),
            eq(List.of("trace rule")));
    }

    @Test
    void createDelegatesToServiceAndPassesExternalRules() {
        CreateFileNodesRequest request = request();

        ApiResponse<Void> response = controller.createFileNodes(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("文件节点创建成功");
        verify(service).handleFileAdded(
            eq("demo"),
            eq("/repo/src/App.java"),
            eq("src/App.java"),
            eq("git@example/demo.git"),
            eq("main"),
            eq(new String[] {"classes"}),
            eq(new String[] {"src/main/java"}),
            eq(List.of("endpoint rule")),
            eq(List.of("trace rule")));
    }

    @Test
    void createMergesUnifiedSerSourcesWithLegacyEndpointRuleSources() {
        CreateFileNodesRequest request = request();
        request.setSerRuleSources(List.of("combined ser"));

        ApiResponse<Void> response = controller.createFileNodes(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(service).handleFileAdded(
            eq("demo"),
            eq("/repo/src/App.java"),
            eq("src/App.java"),
            eq("git@example/demo.git"),
            eq("main"),
            eq(new String[] {"classes"}),
            eq(new String[] {"src/main/java"}),
            eq(List.of("combined ser", "endpoint rule")),
            eq(List.of("trace rule")));
    }

    @Test
    void createRejectsMissingProjectName() {
        CreateFileNodesRequest request = request();
        request.setProjectName("");

        ApiResponse<Void> response = controller.createFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("项目名称不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void createReturnsErrorWhenServiceFails() {
        CreateFileNodesRequest request = request();
        doThrow(new RuntimeException("create failed")).when(service)
            .handleFileAdded(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ApiResponse<Void> response = controller.createFileNodes(request);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("创建文件节点失败: create failed");
    }

    @Test
    void updateReturnsErrorWhenServiceFails() {
        CreateFileNodesRequest request = request();
        doThrow(new RuntimeException("parse failed")).when(service)
            .handleFileModified(any(), any(), any(), any(), any(), any(), any(), any(), any());

        ApiResponse<Void> response = controller.updateFileNodes(request);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("更新文件节点失败: parse failed");
    }

    @Test
    void deleteRejectsMissingProjectFilePath() {
        CreateFileNodesRequest request = request();
        request.setProjectFilePath(" ");

        ApiResponse<Void> response = controller.deleteFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("项目相对路径不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void deleteRejectsMissingProjectName() {
        CreateFileNodesRequest request = request();
        request.setProjectName(null);

        ApiResponse<Void> response = controller.deleteFileNodes(request);

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).isEqualTo("项目名称不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void deleteDelegatesToServiceWithEmptyPathArraysWhenListsAreNull() {
        CreateFileNodesRequest request = request();
        request.setClasspathEntries(null);
        request.setSourcepathEntries(null);

        ApiResponse<Void> response = controller.deleteFileNodes(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("文件节点删除成功");
        verify(service).handleFileDeleted(
            eq("demo"),
            eq("/repo/src/App.java"),
            eq("src/App.java"),
            eq("git@example/demo.git"),
            eq("main"),
            eq(new String[] {}),
            eq(new String[] {}));
    }

    @Test
    void deleteReturnsErrorWhenServiceFails() {
        CreateFileNodesRequest request = request();
        doThrow(new RuntimeException("delete failed")).when(service)
            .handleFileDeleted(any(), any(), any(), any(), any(), any(), any());

        ApiResponse<Void> response = controller.deleteFileNodes(request);

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getMessage()).contains("删除文件节点失败: delete failed");
    }

    @Test
    void apiResponseFactoriesSetExpectedFields() {
        assertThat(ApiResponse.success("data")).extracting("code", "message", "data")
            .containsExactly(200, "操作成功", "data");
        assertThat(ApiResponse.error("bad")).extracting("code", "message", "data")
            .containsExactly(500, "bad", null);
    }

    private CreateFileNodesRequest request() {
        CreateFileNodesRequest request = new CreateFileNodesRequest();
        request.setProjectName("demo");
        request.setAbsoluteFilePath("/repo/src/App.java");
        request.setProjectFilePath("src/App.java");
        request.setGitRepoUrl("git@example/demo.git");
        request.setGitBranch("main");
        request.setClasspathEntries(List.of("classes"));
        request.setSourcepathEntries(List.of("src/main/java"));
        request.setEndpointRuleSources(List.of("endpoint rule"));
        request.setTraceRuleSources(List.of("trace rule"));
        return request;
    }
}
