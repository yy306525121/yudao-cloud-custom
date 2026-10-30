package cn.iocoder.yudao.framework.encrypt.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.*;
import java.util.function.UnaryOperator;

/** 严格的属性路径子集，不执行 JSONPath 表达式。 */
public final class FieldPaths {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private FieldPaths() { }
    public static byte[] transform(byte[] json, String[] paths, UnaryOperator<String> operation) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isContainerNode()) throw new IllegalArgumentException("需要 JSON 对象或数组");
            Set<String> visited = new HashSet<>();
            for (String path : paths) {
                if (path == null || !path.matches("[A-Za-z_][A-Za-z0-9_]*(\\[\\*\\])?(\\.[A-Za-z_][A-Za-z0-9_]*(\\[\\*\\])?)*")) {
                    throw new IllegalArgumentException("非法字段路径: " + path);
                }
                for (String segment : path.split("\\.")) {
                    if (Set.of("__proto__", "prototype", "constructor").contains(segment.replace("[*]", ""))) {
                        throw new IllegalArgumentException("非法字段路径: " + path);
                    }
                }
                walk(root, path.split("\\."), 0, "", visited, operation);
            }
            return MAPPER.writeValueAsBytes(root);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("字段加解密需要有效 JSON", ex); }
    }
    private static void walk(JsonNode node, String[] parts, int offset, String location,
                             Set<String> visited, UnaryOperator<String> operation) {
        if (node == null || node.isNull()) return;
        if (!node.isObject()) throw new IllegalArgumentException("字段路径经过非对象: " + location);
        String part = parts[offset]; boolean array = part.endsWith("[*]");
        String name = array ? part.substring(0, part.length() - 3) : part;
        JsonNode child = node.get(name);
        if (child == null || child.isNull()) return;
        String next = location + "/" + name;
        if (array) {
            if (!child.isArray()) throw new IllegalArgumentException("字段不是数组: " + next);
            for (int i = 0; i < child.size(); i++) {
                if (offset == parts.length - 1) {
                    JsonNode value = child.get(i);
                    if (!value.isNull() && visited.add(next + "/" + i)) {
                        if (!value.isTextual()) throw new IllegalArgumentException("加密字段必须是字符串: " + next);
                        ((ArrayNode) child).set(i, TextNode.valueOf(operation.apply(value.textValue())));
                    }
                } else walk(child.get(i), parts, offset + 1, next + "/" + i, visited, operation);
            }
        } else if (offset == parts.length - 1) {
            if (visited.add(next)) {
                if (!child.isTextual()) throw new IllegalArgumentException("加密字段必须是字符串: " + next);
                ((ObjectNode) node).put(name, operation.apply(child.textValue()));
            }
        } else walk(child, parts, offset + 1, next, visited, operation);
    }
}
