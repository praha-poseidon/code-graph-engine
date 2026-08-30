package com.poseidon.codegraph.app.task;

import com.poseidon.codegraph.app.config.RepositoryConfig;
import com.poseidon.codegraph.app.config.RepositoryConfigStore;
import com.poseidon.codegraph.starter.service.IncrementalUpdateService;
import com.poseidon.codegraph.starter.service.IncrementalUpdateSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "code-graph.tasks.enabled", havingValue = "true", matchIfMissing = true)
public final class AnalysisTaskWorker {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
        ".git", ".idea", ".vscode", "node_modules", "vendor", "target", "build", "dist", ".build");

    private final AnalysisTaskStore taskStore;
    private final RepositoryConfigStore repositoryStore;
    private final GitWorkspace workspace;
    private final IncrementalUpdateService incrementalUpdateService;
    private final boolean autoBuild;
    private volatile boolean ready;

    public AnalysisTaskWorker(
            AnalysisTaskStore taskStore,
            RepositoryConfigStore repositoryStore,
            GitWorkspace workspace,
            IncrementalUpdateService incrementalUpdateService,
            @Value("${code-graph.tasks.auto-build:true}") boolean autoBuild) {
        this.taskStore = taskStore;
        this.repositoryStore = repositoryStore;
        this.workspace = workspace;
        this.incrementalUpdateService = incrementalUpdateService;
        this.autoBuild = autoBuild;
    }

    @Scheduled(fixedDelayString = "${code-graph.tasks.poll-delay-ms:1000}")
    public void poll() {
        if (!ready) {
            return;
        }
        taskStore.claimNext().ifPresent(this::execute);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        int recovered = taskStore.requeueInterrupted();
        if (recovered > 0) {
            log.info("已将 {} 个中断的分析任务重新放回队列", recovered);
        }
        ready = true;
    }

    void execute(AnalysisTask task) {
        RepositoryConfig encrypted = repositoryStore.findById(task.repositoryId()).orElse(null);
        if (encrypted == null) {
            taskStore.fail(task.id(), "仓库不存在", "repositoryId=" + task.repositoryId());
            return;
        }
        repositoryStore.updateStatus(encrypted.id(), "ANALYZING", encrypted.lastAnalyzedAt());
        Map<String, IncrementalUpdateSession> sessions = new HashMap<>();
        try {
            RepositoryConfig repository = repositoryStore.decrypted(encrypted);
            taskStore.updateProgress(task.id(), 0, 0, "克隆仓库");
            Path checkout = workspace.cloneRepository(task.id(), repository);

            List<String> classpath = prepareBuild(task.id(), checkout, repository);
            List<Path> sourceFiles = sourceFiles(checkout, repository.languages());
            if (sourceFiles.isEmpty()) {
                throw new IllegalStateException("仓库中没有找到所选语言的源码文件");
            }

            int total = sourceFiles.size();
            taskStore.updateProgress(task.id(), 0, total, "开始解析 " + total + " 个文件");
            String[] sourceRoots = sourceRoots(checkout);
            String[] dependencies = classpath.toArray(String[]::new);
            int current = 0;
            for (Path file : sourceFiles) {
                String projectFilePath = checkout.relativize(file).toString().replace('\\', '/');
                String language = languageFor(file);
                IncrementalUpdateSession session = sessions.computeIfAbsent(
                    language, incrementalUpdateService::openSession);
                session.handleFileAdded(
                    repository.name(),
                    file.toAbsolutePath().normalize().toString(),
                    projectFilePath,
                    repository.gitRepoUrl(),
                    repository.gitBranch(),
                    dependencies,
                    sourceRoots,
                    repository.endpointRuleSources(),
                    List.of());
                current++;
                taskStore.updateProgress(task.id(), current, total,
                    "正在解析 " + projectFilePath);
            }

            taskStore.succeed(task.id(), total);
            repositoryStore.updateStatus(repository.id(), "DONE", Instant.now());
        } catch (Exception exception) {
            log.error("异步分析任务失败: taskId={}, repositoryId={}", task.id(), task.repositoryId(), exception);
            String message = rootMessage(exception);
            taskStore.fail(task.id(), message, stackSummary(exception));
            repositoryStore.updateStatus(encrypted.id(), "FAILED", encrypted.lastAnalyzedAt());
        } finally {
            sessions.values().forEach(session -> {
                try {
                    session.close();
                } catch (Exception exception) {
                    log.warn("关闭 parser session 失败: taskId={}, language={}", task.id(), session.language(), exception);
                }
            });
            workspace.cleanup(task.id());
        }
    }

    private List<String> prepareBuild(String taskId, Path checkout, RepositoryConfig repository) {
        if (!autoBuild || repository.languages().stream().noneMatch(language ->
                "java".equals(language) || "kotlin".equals(language))) {
            return existingClasspath(checkout);
        }
        if (Files.exists(checkout.resolve("pom.xml"))) {
            taskStore.updateProgress(taskId, 0, 0, "执行 Maven 构建并解析依赖");
            String executable = Files.isRegularFile(checkout.resolve("mvnw")) ? "./mvnw" : "mvn";
            workspace.run(List.of(executable, "-q", "-DskipTests", "package",
                "dependency:build-classpath", "-Dmdep.outputFile=.codegraph-classpath"),
                checkout, Map.of(), Duration.ofMinutes(15));
        } else if (Files.exists(checkout.resolve("build.gradle")) || Files.exists(checkout.resolve("build.gradle.kts"))) {
            taskStore.updateProgress(taskId, 0, 0, "执行 Gradle 构建");
            String executable = Files.isRegularFile(checkout.resolve("gradlew")) ? "./gradlew" : "gradle";
            workspace.run(List.of(executable, "classes", "--no-daemon"),
                checkout, Map.of(), Duration.ofMinutes(15));
        }
        return existingClasspath(checkout);
    }

    private List<String> existingClasspath(Path checkout) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        addIfDirectory(entries, checkout.resolve("target/classes"));
        addIfDirectory(entries, checkout.resolve("build/classes/java/main"));
        addIfDirectory(entries, checkout.resolve("build/classes/kotlin/main"));
        Path classpathFile = checkout.resolve(".codegraph-classpath");
        if (Files.isRegularFile(classpathFile)) {
            try {
                String raw = Files.readString(classpathFile).trim();
                if (!raw.isBlank()) {
                    for (String entry : raw.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
                        if (!entry.isBlank()) entries.add(entry.trim());
                    }
                }
            } catch (IOException exception) {
                throw new IllegalStateException("读取 Maven classpath 失败", exception);
            }
        }
        return List.copyOf(entries);
    }

    private void addIfDirectory(Set<String> entries, Path path) {
        if (Files.isDirectory(path)) entries.add(path.toAbsolutePath().normalize().toString());
    }

    private List<Path> sourceFiles(Path checkout, List<String> selectedLanguages) {
        Set<String> languages = selectedLanguages.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        try (var paths = Files.walk(checkout)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> !ignored(checkout, path))
                .filter(path -> languages.contains(languageFor(path)))
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("扫描源码文件失败", exception);
        }
    }

    private boolean ignored(Path checkout, Path path) {
        for (Path segment : checkout.relativize(path)) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) return true;
        }
        return false;
    }

    private String[] sourceRoots(Path checkout) {
        List<String> roots = new ArrayList<>();
        roots.add(checkout.toAbsolutePath().normalize().toString());
        for (String relative : List.of("src/main/java", "src/main/kotlin", "src", "Sources")) {
            Path candidate = checkout.resolve(relative);
            if (Files.isDirectory(candidate)) roots.add(candidate.toAbsolutePath().normalize().toString());
        }
        return roots.toArray(String[]::new);
    }

    static String languageFor(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".php")) return "php";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
        if (name.endsWith(".swift")) return "swift";
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs") || name.endsWith(".cjs")) {
            return "javascript";
        }
        if (name.endsWith(".ts") || name.endsWith(".tsx") || name.endsWith(".mts") || name.endsWith(".cts")) {
            return "typescript";
        }
        return "unknown";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank()
            ? current.getClass().getSimpleName()
            : current.getMessage();
    }

    private String stackSummary(Throwable throwable) {
        StringBuilder output = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (!output.isEmpty()) output.append("\nCaused by: ");
            output.append(current.getClass().getName()).append(": ").append(current.getMessage());
            current = current.getCause();
        }
        return output.length() > 12000 ? output.substring(0, 12000) : output.toString();
    }
}
