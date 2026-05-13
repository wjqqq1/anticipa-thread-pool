package com.baomihuahua.anticipa.agent.knowledge;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于 Markdown 文件的知识库检索器。
 * <p>
 * 采用 grep 式加权评分算法，根据关键词命中位置、来源类型、上下文匹配度综合打分。
 * 知识库（knowledge）的通用最佳实践是默认首选；
 * 当上下文高度匹配时（同一线程池/业务类型），记忆（memory）可以反超知识。
 * </p>
 */
public class FileKnowledgeRetriever implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(FileKnowledgeRetriever.class);

    private final Path knowledgeDir;
    private final Path memoryDir;

    public FileKnowledgeRetriever(String knowledgeDir, String memoryDir) {
        this.knowledgeDir = Paths.get(knowledgeDir);
        this.memoryDir = Paths.get(memoryDir);
    }

    @Override
    public List<KnowledgeDocument> search(List<String> queries, SearchContext context, int topK) {
        List<KnowledgeDocument> results = new ArrayList<>();
        // 1. 检索知识库
        if (Files.exists(knowledgeDir)) {
            results.addAll(searchInDir(knowledgeDir, queries, context, "knowledge"));
        }
        // 2. 检索记忆库
        if (Files.exists(memoryDir)) {
            results.addAll(searchInDir(memoryDir, queries, context, "memory"));
        }
        // 3. 按得分排序
        results.sort(Comparator.comparingDouble(KnowledgeDocument::getScore).reversed());
        return results.subList(0, Math.min(topK, results.size()));
    }

    private List<KnowledgeDocument> searchInDir(Path dir, List<String> queries,
                                                  SearchContext ctx, String source) {
        List<KnowledgeDocument> results = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir, 1)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file, StandardCharsets.UTF_8);
                            KnowledgeDocument doc = parseDocument(content, file);
                            double score = calculateScore(content, doc, queries, ctx, source);
                            if (score > 0) {
                                doc.setScore(score);
                                results.add(doc);
                            }
                        } catch (IOException e) {
                            log.warn("[Knowledge] failed to read file: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("[Knowledge] failed to walk dir: {}", dir, e);
        }
        results.sort(Comparator.comparingDouble(KnowledgeDocument::getScore).reversed());
        return results;
    }

    /**
     * 解析 Markdown 文件的 YAML frontmatter + 正文。
     */
    private KnowledgeDocument parseDocument(String content, Path file) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setContent(content);

        // 提取文件名作为默认标题
        String fileName = file.getFileName().toString().replace(".md", "");
        doc.setTitle(fileName);

        // 解析 YAML frontmatter (--- 包围的部分)
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String yaml = content.substring(3, endIndex).trim();
                // 简单的 key: value 解析
                for (String line : yaml.split("\n")) {
                    line = line.trim();
                    if (line.contains(": ")) {
                        String key = line.substring(0, line.indexOf(": ")).trim();
                        String value = line.substring(line.indexOf(": ") + 2).trim();
                        setField(doc, key, value);
                    }
                }
            }
        }
        return doc;
    }

    private void setField(KnowledgeDocument doc, String key, String value) {
        switch (key) {
            case "title" -> doc.setTitle(value);
            case "type" -> doc.setType(value);
            case "category" -> doc.setCategory(value);
            case "tags" -> {
                String tags = value.replace("[", "").replace("]", "").replace("\"", "");
                doc.setTags(Arrays.asList(tags.split(",\s*")));
            }
            case "businessType" -> doc.setBusinessType(value);
            case "priority" -> doc.setPriority(value);
            case "source" -> doc.setSource(value);
            case "threadPoolId" -> doc.setThreadPoolId(value);
            case "action" -> doc.setAction(value);
            case "severity" -> doc.setSeverity(value);
            case "date" -> {
                try {
                    doc.setDate(LocalDate.parse(value, DateTimeFormatter.ISO_DATE));
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 加权评分算法。
     */
    private double calculateScore(String content, KnowledgeDocument doc,
                                   List<String> queries, SearchContext ctx, String source) {
        if (queries == null || queries.isEmpty()) return 0;

        String contentLower = content.toLowerCase();
        long matchCount = queries.stream()
                .filter(q -> q != null && !q.isEmpty())
                .filter(q -> contentLower.contains(q.toLowerCase()))
                .count();
        if (matchCount == 0) return 0;

        // 基础得分：关键词命中率
        double score = (double) matchCount / queries.size();

        // ── 匹配位置加权 ──
        if (hasTagMatch(doc, queries)) score *= 2.0;          // tags 命中
        else if (hasTitleMatch(doc, queries)) score *= 1.8;   // title 命中
        else if (hasHeadingMatch(content, queries)) score *= 1.5; // 标题行命中

        // ── 来源加权：知识库 > 记忆（默认） ──
        if ("knowledge".equals(source)) score *= 1.3;

        // ── 上下文提权：记忆可以反超知识 ──
        if (ctx.getThreadPoolId() != null && content.contains(ctx.getThreadPoolId()))
            score *= 2.0;
        if (ctx.getBusinessType() != null && content.contains(ctx.getBusinessType()))
            score *= 1.5;

        // 时间回溯查询 → 记忆权重提高
        if ("memory".equals(source) && hasTemporalQuery(queries))
            score *= 1.3;

        return score;
    }

    private boolean hasTagMatch(KnowledgeDocument doc, List<String> queries) {
        if (doc.getTags() == null) return false;
        return queries.stream().anyMatch(q ->
                doc.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(q.toLowerCase())));
    }

    private boolean hasTitleMatch(KnowledgeDocument doc, List<String> queries) {
        if (doc.getTitle() == null) return false;
        String title = doc.getTitle().toLowerCase();
        return queries.stream().anyMatch(q -> title.contains(q.toLowerCase()));
    }

    private boolean hasHeadingMatch(String content, List<String> queries) {
        String lower = content.toLowerCase();
        return queries.stream().anyMatch(q ->
                Arrays.stream(lower.split("\n"))
                        .filter(line -> line.startsWith("##") || line.startsWith("#"))
                        .anyMatch(heading -> heading.contains(q.toLowerCase())));
    }

    private boolean hasTemporalQuery(List<String> queries) {
        List<String> temporalWords = List.of("昨天", "今天", "前天", "最近", "历史", "上次", "之前", "过去");
        return queries.stream().anyMatch(q ->
                temporalWords.stream().anyMatch(t -> q.contains(t)));
    }
}
