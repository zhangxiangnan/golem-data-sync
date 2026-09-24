package io.golem.datasync.lab;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.golem.datasync.api.RequestValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class LabJson {
    static final ObjectMapper JSON = new ObjectMapper();
    static ObjectNode object() { return JSON.createObjectNode(); }
    static JsonNode parse(String value) {
        try { return JSON.readTree(value); }
        catch (Exception e) { throw new RequestValidationException("JSON 语法不正确"); }
    }
    static ObjectNode obj(String value) { return (ObjectNode) parse(value); }
    static String digest(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(value).toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = object(); var keys = new TreeSet<String>(); value.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> result.set(key, canonical(value.get(key)))); return result;
        }
        if (value.isArray()) { var result=JSON.createArrayNode(); value.forEach(v -> result.add(canonical(v))); return result; }
        return value;
    }
    static boolean sensitive(String key) { return key.toLowerCase(Locale.ROOT).matches(".*(password|passwd|secret|api[_-]?key|access[_-]?key|token|credential).*" ); }
    static JsonNode redact(JsonNode value, Collection<String> secrets) {
        if (value == null) return NullNode.instance;
        if (value.isObject()) {
            var result=object(); value.fields().forEachRemaining(e -> result.set(e.getKey(), sensitive(e.getKey()) ? TextNode.valueOf("******") : redact(e.getValue(), secrets))); return result;
        }
        if (value.isArray()) { var result=JSON.createArrayNode(); value.forEach(v -> result.add(redact(v,secrets))); return result; }
        if (value.isTextual()) return TextNode.valueOf(scrub(value.asText(),secrets));
        return value.deepCopy();
    }
    static String scrub(String value, Collection<String> secrets) {
        String result=value == null ? "" : value;
        for (String secret: secrets) if(secret!=null && !secret.isEmpty()) result=result.replace(secret,"******");
        return result.replaceAll("(?i)((?:password|passwd|pwd|secret|api_key|access_key|token)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;&\\\"']+", "$1******");
    }
    static List<String> secretValues(JsonNode value) {
        List<String> result=new ArrayList<>(); collect(value,result); return result;
    }
    private static void collect(JsonNode value,List<String> result) {
        if(value.isObject()) value.fields().forEachRemaining(e -> { if(sensitive(e.getKey()) && e.getValue().isTextual()) result.add(e.getValue().asText()); else collect(e.getValue(),result); });
        else if(value.isArray()) value.forEach(v -> collect(v,result));
    }
}
