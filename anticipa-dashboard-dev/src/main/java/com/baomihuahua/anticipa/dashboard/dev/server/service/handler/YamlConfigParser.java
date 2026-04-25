package com.baomihuahua.anticipa.dashboard.dev.server.service.handler;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@Component
public class YamlConfigParser {

    private static final String INDEX_PREFIX = "[";
    private static final String INDEX_SUFFIX = "]";
    private static final String PATH_SEPARATOR = ".";

    public Map<Object, Object> doParse(String configuration) {
        return Optional.ofNullable(configuration)
                .filter(StrUtil::isNotEmpty)
                .map(this::parseYamlDocument)
                .map(this::normalizeHierarchy)
                .orElseGet(Collections::emptyMap);
    }

    private Map<Object, Object> parseYamlDocument(String content) {
        return Optional.ofNullable(new Yaml().load(content))
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<Object, Object>) obj)
                .filter(map -> !MapUtil.isEmpty(map))
                .orElseGet(Collections::emptyMap);
    }

    private Map<Object, Object> normalizeHierarchy(Map<Object, Object> nestedData) {
        Map<Object, Object> flattenedData = new LinkedHashMap<>();
        processNestedElements(flattenedData, nestedData, null);
        return flattenedData;
    }

    private void processNestedElements(Map<Object, Object> target, Object current, String currentPath) {
        if (current instanceof Map) {
            handleMapEntries(target, (Map<?, ?>) current, currentPath);
        } else if (current instanceof Iterable) {
            handleCollectionItems(target, (Iterable<?>) current, currentPath);
        } else {
            persistLeafValue(target, currentPath, current);
        }
    }

    private void handleMapEntries(Map<Object, Object> target, Map<?, ?> entries, String parentPath) {
        entries.forEach((key, value) ->
                processNestedElements(target, value, buildPathSegment(parentPath, key))
        );
    }

    private void handleCollectionItems(Map<Object, Object> target, Iterable<?> items, String basePath) {
        List<?> elements = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toList());
        IntStream.range(0, elements.size())
                .forEach(index -> processNestedElements(
                        target,
                        elements.get(index),
                        createIndexedPath(basePath, index)
                ));
    }

    private String buildPathSegment(String existingPath, Object key) {
        return existingPath == null ?
                key.toString() :
                existingPath + PATH_SEPARATOR + key;
    }

    private String createIndexedPath(String basePath, int index) {
        return basePath + INDEX_PREFIX + index + INDEX_SUFFIX;
    }

    private void persistLeafValue(Map<Object, Object> target, String path, Object value) {
        if (path != null) {
            String normalizedPath = path.replace(PATH_SEPARATOR + INDEX_PREFIX, INDEX_PREFIX);
            target.put(normalizedPath, value != null ? value.toString() : null);
        }
    }
}
