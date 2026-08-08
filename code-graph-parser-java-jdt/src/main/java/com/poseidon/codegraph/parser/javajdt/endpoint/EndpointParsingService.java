package com.poseidon.codegraph.parser.javajdt.endpoint;

import com.poseidon.codegraph.model.CodeEndpoint;
import com.poseidon.codegraph.parser.javajdt.endpoint.mapper.StaticExtractEndpointMapper;
import com.poseidon.javastatic.extract.jdt.DefaultJdtStaticExtractEngine;
import com.poseidon.javastatic.extract.jdt.JdtStaticExtractEngine;
import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.jdt.load.SerRuleLoader;
import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.language.SerRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runs static-extract-java SER rules on JDT AST and maps results to graph endpoints.
 *
 * <p>Uses the current static-extract-java model:
 * <ul>
 *   <li>No built-in SER rules (caller supplies text or empty list).</li>
 *   <li>Value-trace lives in the same rule as optional {@code trace { ... }}.</li>
 *   <li>External config dictionary is per-call via {@code externalValues}.</li>
 * </ul>
 */
@Service
@Slf4j
public class EndpointParsingService {

    private final SerRuleLoader staticRuleLoader;
    private final JdtStaticExtractEngine staticExtractEngine;
    private final SerRuleParser serRuleParser;

    private List<StaticExtractRule> staticRules = List.of();

    public EndpointParsingService() {
        this(new SerRuleLoader(), new DefaultJdtStaticExtractEngine());
    }

    public EndpointParsingService(SerRuleLoader staticRuleLoader, JdtStaticExtractEngine staticExtractEngine) {
        this.staticRuleLoader = staticRuleLoader != null ? staticRuleLoader : new SerRuleLoader();
        this.staticExtractEngine = staticExtractEngine != null ? staticExtractEngine : new DefaultJdtStaticExtractEngine();
        this.serRuleParser = new AntlrSerRuleParser();
    }

    public EndpointParsingService(List<String> endpointRuleSources, List<String> traceRuleSources) {
        this(endpointRuleSources, traceRuleSources, false);
    }

    public EndpointParsingService(
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        this(new SerRuleLoader(), new DefaultJdtStaticExtractEngine(), endpointRuleSources, traceRuleSources, includeBuiltinRules);
    }

    public EndpointParsingService(
            SerRuleLoader staticRuleLoader,
            JdtStaticExtractEngine staticExtractEngine,
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        this(staticRuleLoader, staticExtractEngine, endpointRuleSources, traceRuleSources, false);
    }

    public EndpointParsingService(
            SerRuleLoader staticRuleLoader,
            JdtStaticExtractEngine staticExtractEngine,
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        this(staticRuleLoader, staticExtractEngine);
        setRuleSources(endpointRuleSources, traceRuleSources, includeBuiltinRules);
    }

    @PostConstruct
    public void init() {
        // Built-ins removed from static-extract-java. Rules come only from setRuleSources / parse request.
        if (staticRules == null) {
            staticRules = List.of();
        }
        log.info("端点解析服务初始化完成，当前 SER 规则 {} 条（无内置规则；由调用方传入）", staticRules.size());
    }

    /**
     * Install SER rule texts. Each source may contain one or more {@code rule "..." ...} documents;
     * optional embedded {@code trace { }} is parsed with the rule (not as a separate file).
     *
     * <p>{@code traceRuleSources} is accepted for API compatibility but ignored for standalone
     * {@code .trace} documents — put patches inside the same rule file.
     */
    public final void setRuleSources(List<String> endpointRuleSources, List<String> traceRuleSources) {
        setRuleSources(endpointRuleSources, traceRuleSources, false);
    }

    public final void setRuleSources(
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        if (includeBuiltinRules) {
            log.warn("includeBuiltinRules=true ignored: static-extract-java no longer ships built-in SER rules");
        }
        List<String> sources = new ArrayList<>();
        if (endpointRuleSources != null) {
            sources.addAll(endpointRuleSources);
        }
        // Legacy: if someone still passes full rule+trace as "trace" list, try parse as rules too
        if (traceRuleSources != null) {
            for (String source : traceRuleSources) {
                if (source != null && source.contains("rule ")) {
                    sources.add(source);
                } else if (source != null && !source.isBlank()) {
                    log.warn("Ignoring standalone trace SER source (use embedded trace {{ }} in the rule file)");
                }
            }
        }
        this.staticRules = List.copyOf(parseRuleSources(sources));
    }

    private List<StaticExtractRule> parseRuleSources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        List<StaticExtractRule> rules = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            String source = sources.get(i);
            if (source == null || source.isBlank()) {
                continue;
            }
            try {
                for (String document : splitRuleDocuments(source)) {
                    rules.add(serRuleParser.parse(document));
                }
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Invalid endpoint SER rule source at index " + i + ": " + e.getMessage(), e);
            }
        }
        return rules;
    }

    /**
     * Split one text blob into one-or-more rule documents. A new document starts at a line beginning
     * with {@code rule }. Embedded {@code trace { }} stays inside the same document.
     */
    static List<String> splitRuleDocuments(String source) {
        List<String> docs = new ArrayList<>();
        StringBuilder current = null;
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("rule ") && (trimmed.length() == 5 || !Character.isLetterOrDigit(trimmed.charAt(5)))) {
                // "rule " + quote or space
            }
            boolean newRule = trimmed.startsWith("rule \"") || trimmed.matches("rule\\s+\".*");
            if (!newRule && trimmed.startsWith("rule ") && trimmed.length() > 5) {
                newRule = true;
            }
            if (newRule) {
                if (current != null && !current.toString().isBlank()) {
                    docs.add(current.toString().strip());
                }
                current = new StringBuilder();
            }
            if (current == null) {
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                throw new IllegalArgumentException("Invalid SER syntax: content must start with rule.");
            }
            current.append(line).append('\n');
        }
        if (current != null && !current.toString().isBlank()) {
            docs.add(current.toString().strip());
        }
        return docs;
    }

    /** @deprecated prefer parseEndpointsForType */
    @Deprecated
    public List<CodeEndpoint> parseEndpoints(
            CompilationUnit cu,
            String packageName,
            String fileName,
            String projectFilePath) {
        TypeDeclaration td = topTypeDeclaration(cu);
        if (td == null) {
            return Collections.emptyList();
        }
        return parseEndpointsForType(td, cu, packageName, fileName, projectFilePath, null);
    }

    public List<CodeEndpoint> parseEndpointsForType(
            TypeDeclaration typeDecl,
            CompilationUnit cu,
            String packageName,
            String fileName,
            String projectFilePath,
            String absoluteFilePath) {
        return parseEndpointsForType(typeDecl, cu, packageName, fileName, projectFilePath, absoluteFilePath, Map.of());
    }

    public List<CodeEndpoint> parseEndpointsForType(
            TypeDeclaration typeDecl,
            CompilationUnit cu,
            String packageName,
            String fileName,
            String projectFilePath,
            String absoluteFilePath,
            Map<String, Map<String, List<String>>> externalValues) {
        if (staticRules == null || staticRules.isEmpty()) {
            log.debug("没有可用的静态提取规则");
            return Collections.emptyList();
        }
        List<CodeEndpoint> out = new ArrayList<>();
        JdtStaticExtractEngine engine = engineFor(externalValues);
        for (StaticExtractRule rule : staticRules) {
            List<StaticExtractResult> results =
                    engine.execute(rule, cu, typeDecl, projectFilePath, absoluteFilePath);
            for (StaticExtractResult result : results) {
                CodeEndpoint ep =
                        StaticExtractEndpointMapper.toCodeEndpoint(result, cu, typeDecl, projectFilePath);
                if (ep != null) {
                    out.add(ep);
                }
            }
        }
        return out;
    }

    private JdtStaticExtractEngine engineFor(Map<String, Map<String, List<String>>> externalValues) {
        MapExternalValueResolver resolver =
                new MapExternalValueResolver(externalValues != null ? externalValues : Map.of());
        // Standalone trace rule sets removed; embedded traces are merged per-rule in the extract engine.
        JdtTraceOptions options = JdtTraceOptions.of(List.of(), resolver).withExtractRules(staticRules);
        return new DefaultJdtStaticExtractEngine(options, staticRules);
    }

    private static TypeDeclaration topTypeDeclaration(CompilationUnit cu) {
        if (cu == null || cu.types().isEmpty()) {
            return null;
        }
        Object t = cu.types().get(0);
        return t instanceof TypeDeclaration td ? td : null;
    }
}
