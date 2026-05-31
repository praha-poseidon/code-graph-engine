package com.poseidon.codegraph.parser.javajdt.endpoint.support;

import lombok.extern.slf4j.Slf4j;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Scans application resource config files into the external value dictionary
 * consumed by static-extract trace rules.
 */
@Slf4j
public final class ExternalConfigValueScanner {

    public static final String CONFIG_NAMESPACE = "config";

    private ExternalConfigValueScanner() {}

    public static Map<String, Map<String, List<String>>> scan(String absoluteFilePath, String projectFilePath) {
        Path root = resolveProjectRoot(absoluteFilePath, projectFilePath);
        if (root == null || !Files.isDirectory(root)) {
            return Map.of();
        }
        Map<String, List<String>> configValues = new LinkedHashMap<>();
        for (Path file : configFiles(root)) {
            readConfigFile(file, configValues);
        }
        if (configValues.isEmpty()) {
            return Map.of();
        }
        log.debug("扫描到配置外部值: root={}, keys={}", root, configValues.size());
        return Map.of(CONFIG_NAMESPACE, configValues);
    }

    private static Path resolveProjectRoot(String absoluteFilePath, String projectFilePath) {
        if (absoluteFilePath == null || absoluteFilePath.isBlank()) {
            return null;
        }
        Path absolute = Path.of(absoluteFilePath).toAbsolutePath().normalize();
        if (projectFilePath != null && !projectFilePath.isBlank()) {
            Path relative = Path.of(projectFilePath).normalize();
            int relativeNames = relative.getNameCount();
            Path root = absolute;
            for (int i = 0; i < relativeNames; i++) {
                root = root.getParent();
                if (root == null) {
                    break;
                }
            }
            if (root != null && Files.isDirectory(root)) {
                return root;
            }
        }
        Path current = Files.isDirectory(absolute) ? absolute : absolute.getParent();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    || Files.exists(current.resolve("build.gradle"))
                    || Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("src/main/resources"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static List<Path> configFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root, 8)) {
            return stream.filter(Files::isRegularFile)
                    .filter(ExternalConfigValueScanner::isApplicationConfig)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (Exception e) {
            log.warn("扫描配置文件失败: root={}, error={}", root, e.getMessage());
            return List.of();
        }
    }

    private static boolean isApplicationConfig(Path path) {
        String normalized = path.toString().replace('\\', '/');
        String fileName = path.getFileName().toString();
        return normalized.contains("/src/main/resources/")
                && (fileName.startsWith("application")
                || fileName.startsWith("bootstrap"))
                && (fileName.endsWith(".properties")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml"));
    }

    private static void readConfigFile(Path file, Map<String, List<String>> out) {
        try {
            String source = Files.readString(file);
            String fileName = file.getFileName().toString();
            if (fileName.endsWith(".properties")) {
                readProperties(source, out);
            } else {
                readYaml(source, out);
            }
        } catch (Exception e) {
            log.warn("读取配置文件失败: file={}, error={}", file, e.getMessage());
        }
    }

    private static void readProperties(String source, Map<String, List<String>> out) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(source));
        for (String name : properties.stringPropertyNames()) {
            put(out, name, properties.getProperty(name));
        }
    }

    private static void readYaml(String source, Map<String, List<String>> out) {
        Deque<YamlLevel> stack = new ArrayDeque<>();
        for (String line : source.split("\\R")) {
            String withoutComment = stripYamlComment(line);
            if (withoutComment.isBlank() || withoutComment.trim().equals("---")) {
                continue;
            }
            int indent = countIndent(withoutComment);
            String trimmed = withoutComment.trim();
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            while (!stack.isEmpty() && stack.peekLast().indent() >= indent) {
                stack.removeLast();
            }
            List<String> path = new ArrayList<>();
            for (YamlLevel level : stack) {
                path.add(level.key());
            }
            path.add(unquote(key));
            String fullKey = String.join(".", path);
            if (value.isEmpty()) {
                stack.addLast(new YamlLevel(indent, unquote(key)));
            } else {
                put(out, fullKey, unquote(value));
            }
        }
    }

    private static String stripYamlComment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (!quoted && c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static void put(Map<String, List<String>> out, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        out.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    private record YamlLevel(int indent, String key) {}
}
