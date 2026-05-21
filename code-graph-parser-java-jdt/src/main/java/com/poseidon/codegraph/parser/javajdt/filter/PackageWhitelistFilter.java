package com.poseidon.codegraph.parser.javajdt.filter;

import com.poseidon.codegraph.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 包白名单过滤器
 * 策略：只保留源码中的调用，或指定白名单包（如司内包）的调用。
 * 其他所有调用（JDK、第三方库等）均被过滤。
 */
@Slf4j
public class PackageWhitelistFilter implements RelationshipFilter {

    private final Set<String> whitelistPrefixes;

    public PackageWhitelistFilter(Set<String> whitelistPrefixes) {
        this.whitelistPrefixes = whitelistPrefixes;
    }

    @Override
    public boolean shouldKeep(CodeRelationship relationship, IMethodBinding targetMethodBinding) {
        if (targetMethodBinding == null || targetMethodBinding.getDeclaringClass() == null) {
            return false;
        }

        // 1. 如果是源码，保留
        if (targetMethodBinding.getDeclaringClass().isFromSource()) {
            return true;
        }

        // 2. 如果在白名单中，保留
        String qName = targetMethodBinding.getDeclaringClass().getQualifiedName();
        if (qName != null) {
            for (String prefix : whitelistPrefixes) {
                if (qName.startsWith(prefix)) {
                    return true;
                }
            }
        }

        // 3. 其他情况（JDK、第三方库），过滤
        return false;
    }
}

