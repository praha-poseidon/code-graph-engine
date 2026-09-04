package com.poseidon.codegraph.app.adapter.controller;

import com.poseidon.codegraph.app.adapter.dto.ApiResponse;
import com.poseidon.codegraph.app.config.RepositoryConfig;
import com.poseidon.codegraph.app.config.RepositoryConfigStore;
import com.poseidon.codegraph.app.config.RepositoryRequest;
import com.poseidon.codegraph.app.config.RepositoryView;
import com.poseidon.codegraph.app.task.AnalysisTask;
import com.poseidon.codegraph.app.task.AnalysisTaskStore;
import com.poseidon.codegraph.app.task.AnalysisWorker;
import com.poseidon.codegraph.app.task.AnalysisWorkerStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RepositoryConfigController {

    private final RepositoryConfigStore repositoryStore;
    private final AnalysisTaskStore taskStore;
    private final AnalysisWorkerStore workerStore;

    public RepositoryConfigController(
            RepositoryConfigStore repositoryStore,
            AnalysisTaskStore taskStore,
            AnalysisWorkerStore workerStore) {
        this.repositoryStore = repositoryStore;
        this.taskStore = taskStore;
        this.workerStore = workerStore;
    }

    @GetMapping("/config/projects")
    public ApiResponse<List<RepositoryView>> repositories() {
        return ApiResponse.success(repositoryStore.findAll().stream().map(this::view).toList());
    }

    @PostMapping("/config/projects")
    public ApiResponse<RepositoryView> create(@RequestBody RepositoryRequest request) {
        try {
            return ApiResponse.success("仓库添加成功", view(repositoryStore.create(request)));
        } catch (DuplicateKeyException exception) {
            return ApiResponse.error(409, "该仓库已经存在");
        } catch (RuntimeException exception) {
            return ApiResponse.error(400, exception.getMessage());
        }
    }

    @PutMapping("/config/projects/{id}")
    public ApiResponse<RepositoryView> update(@PathVariable long id, @RequestBody RepositoryRequest request) {
        try {
            return ApiResponse.success("仓库更新成功", view(repositoryStore.update(id, request)));
        } catch (DuplicateKeyException exception) {
            return ApiResponse.error(409, "该仓库已经存在");
        } catch (RuntimeException exception) {
            return ApiResponse.error(400, exception.getMessage());
        }
    }

    @DeleteMapping("/config/projects/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        repositoryStore.delete(id);
        return ApiResponse.success("仓库删除成功", null);
    }

    @PostMapping("/config/projects/{id}/analyze")
    public ApiResponse<AnalysisTask> analyze(@PathVariable long id) {
        if (repositoryStore.findById(id).isEmpty()) {
            return ApiResponse.error(404, "仓库不存在");
        }
        AnalysisTask task = taskStore.activeForRepository(id).orElseGet(() -> taskStore.enqueue(id));
        repositoryStore.updateStatus(id, "ANALYZING", repositoryStore.findById(id).orElseThrow().lastAnalyzedAt());
        return ApiResponse.success("分析任务已进入队列", task);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<AnalysisTask>> tasks(@RequestParam(required = false) Long repositoryId) {
        return ApiResponse.success(repositoryId == null
            ? taskStore.findRecent()
            : taskStore.findByRepository(repositoryId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AnalysisTask> task(@PathVariable String taskId) {
        return taskStore.findById(taskId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.error(404, "任务不存在"));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<AnalysisTask> cancelTask(@PathVariable String taskId) {
        return taskStore.cancel(taskId)
            .map(task -> ApiResponse.success("取消请求已提交", task))
            .orElseGet(() -> ApiResponse.error(404, "任务不存在"));
    }

    @GetMapping("/workers")
    public ApiResponse<List<AnalysisWorker>> workers() {
        return ApiResponse.success(workerStore.findAll());
    }

    private RepositoryView view(RepositoryConfig repository) {
        AnalysisTask latest = taskStore.latestForRepository(repository.id()).orElse(null);
        return new RepositoryView(
            repository.id(),
            repository.name(),
            repository.gitRepoUrl(),
            repository.gitBranch(),
            repository.languages(),
            repository.authType(),
            repository.accessToken() != null && !repository.accessToken().isBlank(),
            repository.sshPrivateKey() != null && !repository.sshPrivateKey().isBlank(),
            repository.endpointRuleSources(),
            status(repository.status(), latest),
            latest == null ? 0 : latest.progressCurrent(),
            latest == null ? 0 : latest.progressTotal(),
            latest == null ? null : latest.message(),
            repository.lastAnalyzedAt(),
            latest == null ? null : latest.id());
    }

    private String status(String repositoryStatus, AnalysisTask latest) {
        if (latest != null) {
            if ("QUEUED".equals(latest.status()) || "RUNNING".equals(latest.status())) return "analyzing";
            if ("SUCCEEDED".equals(latest.status())) return "done";
            if ("FAILED".equals(latest.status())) return "failed";
            if ("CANCELED".equals(latest.status())) return "idle";
        }
        if ("IDLE".equals(repositoryStatus)) return "idle";
        if ("DONE".equals(repositoryStatus)) return "done";
        if ("FAILED".equals(repositoryStatus)) return "failed";
        if ("ANALYZING".equals(repositoryStatus)) return "analyzing";
        return "idle";
    }
}
