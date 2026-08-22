package com.poseidon.codegraph.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for parser implementations.
 */
public final class CodeGraphParserRegistry {

    private final Map<String, CodeGraphParser> parsers;

    public CodeGraphParserRegistry(List<CodeGraphParser> parsers) {
        this.parsers = parsers == null
                ? Map.of()
                : parsers.stream()
                        .collect(Collectors.toUnmodifiableMap(
                                parser -> normalize(parser.language()),
                                Function.identity(),
                                (left, right) -> left));
    }

    public static CodeGraphParserRegistry loadFromServiceLoader() {
        List<CodeGraphParser> parsers = new ArrayList<>();
        ServiceLoader.load(CodeGraphParser.class).forEach(parsers::add);
        ServiceLoader.load(CodeGraphParserProvider.class).forEach(provider -> {
            List<CodeGraphParser> providedParsers = provider.parsers();
            if (providedParsers != null) {
                parsers.addAll(providedParsers);
            }
        });
        return new CodeGraphParserRegistry(parsers);
    }

    public Optional<CodeGraphParser> find(String language) {
        return Optional.ofNullable(parsers.get(normalize(language)));
    }

    public CodeGraphParserSession openSession(String language) {
        return find(language)
                .orElseThrow(() -> new IllegalStateException(
                        "No code graph parser registered for language: " + language))
                .openSession();
    }

    /** Returns a task-local registry with the selected parser replaced. */
    public CodeGraphParserRegistry withParser(CodeGraphParser parser) {
        List<CodeGraphParser> values = new ArrayList<>(parsers.values());
        values.removeIf(existing -> normalize(existing.language()).equals(normalize(parser.language())));
        values.add(parser);
        return new CodeGraphParserRegistry(values);
    }

    public List<String> languages() {
        return List.copyOf(parsers.keySet());
    }

    private static String normalize(String language) {
        return language == null ? "" : language.toLowerCase(Locale.ROOT);
    }
}
