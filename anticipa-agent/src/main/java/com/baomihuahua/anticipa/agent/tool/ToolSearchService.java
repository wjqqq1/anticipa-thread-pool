package com.baomihuahua.anticipa.agent.tool;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具检索服务。
 * <p>
 * 返回全量工具，按关键词相关性排序，将与用户输入最相关的工具排在前面，
 * 帮助 LLM 更快定位合适的工具。
 * </p>
 */
public class ToolSearchService {

    private static final Logger log = LoggerFactory.getLogger(ToolSearchService.class);

    private final ToolRegistry toolRegistry;

    public ToolSearchService(ToolRegistry toolRegistry, int maxToolsPerRequest, boolean fallbackToAll) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 根据用户输入，返回全量工具并按关键词相关性排序。
     *
     * @param userMessage 用户消息原文
     * @return 按相关性排序的工具列表
     */
    public List<ToolDefinition> search(String userMessage) {
        List<ToolDefinition> allTools = toolRegistry.getAllTools();
        if (allTools.isEmpty()) {
            return Collections.emptyList();
        }

        // 按关键词相关性排序，最相关的工具排在前面
        List<ToolDefinition> ranked = rankByRelevance(allTools, userMessage);

        log.debug("[ToolSearch] candidates={}", ranked.stream()
                .map(ToolDefinition::getName).collect(Collectors.toList()));
        return ranked;
    }

    /**
     * 按关键词相关性对工具排序。
     * <p>
     * 与用户输入匹配度越高的工具排在越前面，帮助 LLM 优先关注相关工具。
     * </p>
     */
    private List<ToolDefinition> rankByRelevance(List<ToolDefinition> tools, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return tools;
        }
        String msgLower = userMessage.toLowerCase();

        return tools.stream()
                .sorted(Comparator.comparingDouble((ToolDefinition t) -> {
                    double score = 0;
                    if (t.getName().toLowerCase().contains(msgLower)) score += 3;
                    if (t.getDescription().toLowerCase().contains(msgLower)) score += 2;
                    if (t.getParameterSchema() != null) {
                        String schemaStr = t.getParameterSchema().toString().toLowerCase();
                        if (schemaStr.contains(msgLower)) score += 1;
                    }
                    return -score; // 降序
                }))
                .collect(Collectors.toList());
    }
}
