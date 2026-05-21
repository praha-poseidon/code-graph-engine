package com.poseidon.codegraph.parser.process;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads process parser configuration from JVM properties or environment variables.
 */
final class ProcessParserConfig {

    static final String LANGUAGES_PROPERTY = "codegraph.parser.process.languages";
    static final String TIMEOUT_PROPERTY = "codegraph.parser.process.timeoutSeconds";

    private ProcessParserConfig() {
    }

    static List<ProcessCodeGraphParser> loadParsers() {
        List<ProcessCodeGraphParser> parsers = new ArrayList<>();
        for (String language : languages()) {
            command(language).ifPresent(command ->
                parsers.add(new ProcessCodeGraphParser(language, command, timeout())));
        }
        return parsers;
    }

    private static List<String> languages() {
        return split(value(LANGUAGES_PROPERTY, "CODEGRAPH_PARSER_PROCESS_LANGUAGES").orElse(""));
    }

    private static Optional<List<String>> command(String language) {
        String normalized = normalize(language);
        String propertyKey = "codegraph.parser.process." + normalized + ".command";
        String envKey = "CODEGRAPH_PARSER_" + normalized.toUpperCase(Locale.ROOT).replace('-', '_') + "_COMMAND";
        return value(propertyKey, envKey)
            .map(ProcessParserConfig::split)
            .filter(parts -> !parts.isEmpty());
    }

    private static Duration timeout() {
        String raw = value(TIMEOUT_PROPERTY, "CODEGRAPH_PARSER_PROCESS_TIMEOUT_SECONDS").orElse("60");
        try {
            long seconds = Long.parseLong(raw);
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException ignored) {
            return Duration.ofSeconds(60);
        }
    }

    private static Optional<String> value(String propertyKey, String envKey) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue.trim());
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue.trim());
        }
        return Optional.empty();
    }

    static List<String> split(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quoteChar = 0;
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if ((ch == '"' || ch == '\'') && (!quoted || quoteChar == ch)) {
                quoted = !quoted;
                quoteChar = quoted ? ch : 0;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String normalize(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    }
}
