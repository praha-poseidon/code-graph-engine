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
import com.poseidon.javastatic.extract.language.SerTraceRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class EndpointParsingService {

    private final SerRuleLoader staticRuleLoader;
    private final JdtStaticExtractEngine staticExtractEngine;
    private final SerRuleParser serRuleParser;
    private final SerTraceRuleParser serTraceRuleParser;

    private List<StaticExtractRule> staticRules;
    private List<StaticTraceRuleSet> staticTraceRules;

    public EndpointParsingService() {
        this(new SerRuleLoader(), new DefaultJdtStaticExtractEngine());
    }

    public EndpointParsingService(SerRuleLoader staticRuleLoader, JdtStaticExtractEngine staticExtractEngine) {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        this.staticRuleLoader = staticRuleLoader;
        this.staticExtractEngine = staticExtractEngine;
        this.serRuleParser = parser;
        this.serTraceRuleParser = parser;
    }

    public EndpointParsingService(
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        this(
                new SerRuleLoader(),
                new DefaultJdtStaticExtractEngine(),
                endpointRuleSources,
                traceRuleSources,
                true);
    }

    public EndpointParsingService(
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        this(
                new SerRuleLoader(),
                new DefaultJdtStaticExtractEngine(),
                endpointRuleSources,
                traceRuleSources,
                includeBuiltinRules);
    }

    public EndpointParsingService(
            SerRuleLoader staticRuleLoader,
            JdtStaticExtractEngine staticExtractEngine,
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        this(staticRuleLoader, staticExtractEngine, endpointRuleSources, traceRuleSources, true);
    }

    public EndpointParsingService(
            SerRuleLoader staticRuleLoader,
            JdtStaticExtractEngine staticExtractEngine,
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        this.staticRuleLoader = staticRuleLoader;
        this.staticExtractEngine = staticExtractEngine;
        this.serRuleParser = parser;
        this.serTraceRuleParser = parser;
        setRuleSources(endpointRuleSources, traceRuleSources, includeBuiltinRules);
    }

    @PostConstruct
    public void init() {
        if (staticRules != null || staticTraceRules != null) {
            log.info(
                    "端点解析服务初始化完成，使用已配置 SER 规则：{} 条规则，{} 条 trace 规则",
                    staticRules != null ? staticRules.size() : 0,
                    staticTraceRules != null ? staticTraceRules.size() : 0);
            return;
        }
        this.staticRules = staticRuleLoader.loadAll();
        this.staticTraceRules = staticRuleLoader.loadApplicationTraceRules();
        log.info(
                "端点解析服务初始化完成，已加载 {} 条 .ser 静态提取规则，{} 条 trace 规则（JDT runtime）",
                staticRules.size(),
                staticTraceRules.size());
    }

    /**
     * 直接传入 SER 规则文本。
     *
     * <p>调用方可以从数据库、配置中心、文件系统或 HTTP 读取规则文本后传入这里；
     * graph engine 不关心规则来源。
     */
    public final void setRuleSources(
            List<String> endpointRuleSources,
            List<String> traceRuleSources) {
        setRuleSources(endpointRuleSources, traceRuleSources, false);
    }

    /**
     * 直接传入 SER 规则文本，并可选择是否追加内置规则。
     */
    public final void setRuleSources(
            List<String> endpointRuleSources,
            List<String> traceRuleSources,
            boolean includeBuiltinRules) {
        List<StaticExtractRule> rules = new ArrayList<>();
        List<StaticTraceRuleSet> traces = new ArrayList<>();
        if (includeBuiltinRules) {
            rules.addAll(staticRuleLoader.loadAll());
            traces.addAll(staticRuleLoader.loadApplicationTraceRules());
        }
        rules.addAll(parseEndpointRuleSources(endpointRuleSources));
        traces.addAll(parseTraceRuleSources(combine(endpointRuleSources, traceRuleSources)));
        this.staticRules = List.copyOf(rules);
        this.staticTraceRules = List.copyOf(traces);
    }

    private List<StaticExtractRule> parseEndpointRuleSources(List<String> endpointRuleSources) {
        if (endpointRuleSources == null) {
            return List.of();
        }
        List<StaticExtractRule> rules = new ArrayList<>();
        for (int i = 0; i < endpointRuleSources.size(); i++) {
            String source = endpointRuleSources.get(i);
            if (source == null || source.isBlank()) {
                continue;
            }
            rules.addAll(parseSerBlocks(
                    source,
                    i,
                    "rule",
                    serRuleParser::parse,
                    "endpoint"));
        }
        return rules;
    }

    private List<StaticTraceRuleSet> parseTraceRuleSources(List<String> traceRuleSources) {
        if (traceRuleSources == null) {
            return List.of();
        }
        List<StaticTraceRuleSet> traces = new ArrayList<>();
        for (int i = 0; i < traceRuleSources.size(); i++) {
            String source = traceRuleSources.get(i);
            if (source == null || source.isBlank()) {
                continue;
            }
            traces.addAll(parseSerBlocks(
                    source,
                    i,
                    "trace",
                    serTraceRuleParser::parseTrace,
                    "trace"));
        }
        return traces;
    }

    private <T> List<T> parseSerBlocks(
            String source,
            int sourceIndex,
            String expectedKind,
            Function<String, T> parser,
            String errorLabel) {
        try {
            return splitSerBlocks(source).stream()
                    .filter(block -> expectedKind.equals(block.kind()))
                    .map(block -> parser.apply(block.source()))
                    .toList();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Invalid " + errorLabel + " SER rule source at index " + sourceIndex + ": " + e.getMessage(),
                    e);
        }
    }

    private static List<String> combine(List<String> endpointRuleSources, List<String> traceRuleSources) {
        List<String> sources = new ArrayList<>();
        if (endpointRuleSources != null) {
            sources.addAll(endpointRuleSources);
        }
        if (traceRuleSources != null) {
            sources.addAll(traceRuleSources);
        }
        return sources;
    }

    private static List<SerBlock> splitSerBlocks(String source) {
        List<SerBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentKind = null;
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.trim();
            String kind = blockKind(trimmed);
            if (kind != null) {
                if (currentKind != null) {
                    blocks.add(new SerBlock(currentKind, current.toString().strip()));
                    current.setLength(0);
                }
                currentKind = kind;
            }
            if (currentKind != null) {
                current.append(line).append('\n');
            } else if (!trimmed.isBlank() && !trimmed.startsWith("#")) {
                throw new IllegalArgumentException("Invalid SER syntax: content must start with rule or trace.");
            }
        }
        if (currentKind != null) {
            blocks.add(new SerBlock(currentKind, current.toString().strip()));
        }
        return blocks;
    }

    private static String blockKind(String trimmedLine) {
        if (trimmedLine.startsWith("rule ")) {
            return "rule";
        }
        if (trimmedLine.startsWith("trace ")) {
            return "trace";
        }
        return null;
    }

    private record SerBlock(String kind, String source) {}

    /** @deprecated 请优先使用 parseEndpointsForType */
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
                        StaticExtractEndpointMapper.toCodeEndpoint(
                                result, cu, typeDecl, projectFilePath);
                if (ep != null) {
                    out.add(ep);
                }
            }
        }
        return out;
    }

    private JdtStaticExtractEngine engineFor(Map<String, Map<String, List<String>>> externalValues) {
        if ((staticTraceRules == null || staticTraceRules.isEmpty())
                && (externalValues == null || externalValues.isEmpty())) {
            return staticExtractEngine;
        }
        JdtTraceOptions traceOptions =
                JdtTraceOptions.of(
                        staticTraceRules != null ? staticTraceRules : List.of(),
                        new MapExternalValueResolver(externalValues != null ? externalValues : Map.of()));
        return new DefaultJdtStaticExtractEngine(traceOptions);
    }

    private static TypeDeclaration topTypeDeclaration(CompilationUnit cu) {
        if (cu == null || cu.types().isEmpty()) {
            return null;
        }
        Object t = cu.types().get(0);
        return t instanceof TypeDeclaration td ? td : null;
    }
}
