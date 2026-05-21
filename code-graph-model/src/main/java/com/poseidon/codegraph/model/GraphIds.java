package com.poseidon.codegraph.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Shared graph ID helpers.
 */
public final class GraphIds {

    public static final String PROJECT_SEPARATOR = "::";

    private GraphIds() {
    }

    public static String scoped(String projectName, String rawId) {
        if (blank(projectName) || blank(rawId) || isScoped(projectName, rawId)) {
            return rawId;
        }
        return projectName + PROJECT_SEPARATOR + rawId;
    }

    public static boolean isScoped(String projectName, String id) {
        return !blank(projectName) && id != null && id.startsWith(projectName + PROJECT_SEPARATOR);
    }

    public static String raw(String projectName, String scopedId) {
        if (!isScoped(projectName, scopedId)) {
            return scopedId;
        }
        return scopedId.substring((projectName + PROJECT_SEPARATOR).length());
    }

    public static String packageId(String packageName) {
        return "pkg:" + normalize(packageName);
    }

    public static String unitId(String qualifiedName) {
        return "unit:" + normalize(qualifiedName);
    }

    public static String functionId(String qualifiedSignature) {
        return "fn:" + normalize(qualifiedSignature);
    }

    public static String endpointId(String direction, String endpointType, String matchIdentity) {
        return "endpoint:" + normalize(direction) + ":" + normalize(endpointType) + ":" + sha1(normalize(matchIdentity));
    }

    public static String relationshipId(String fromNodeId, RelationshipType type, String toNodeId) {
        return "rel:" + sha1(normalize(fromNodeId) + "|" + (type == null ? "" : type.name()) + "|" + normalize(toNodeId));
    }

    public static String placeholderFunctionId(String qualifiedSignature) {
        return "placeholder:" + functionId(qualifiedSignature);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest is not available", e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
