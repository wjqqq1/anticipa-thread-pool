package com.baomihuahua.anticipa.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于 Markdown 文件的记忆持久化存储。
 * <p>
 * 将长期记忆写为结构化 Markdown 文件（YAML frontmatter + 正文），
 * 通过 grep 式关键词加权评分进行检索。
 * </p>
 */
public class FileMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(FileMemoryStore.class);

    private final Path memoryDir;

    public FileMemoryStore(String memoryDir) {
        this.memoryDir = Paths.get(memoryDir);
        try {
            Files.createDirectories(this.memoryDir);
        } catch (IOException e) {
            log.warn("[Memory] failed to create memory dir: {}", memoryDir, e);
        }
    }

    @Override
    public void save(MemoryEntry entry) {
        try {
            Files.createDirectories(memoryDir);
            String filename = entry.getThreadPoolId() + "-"
                    + entry.getAction() + "-"
                    + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + ".md";

            // 同文件追加：如果今天已有记录，追加内容而非覆盖
            Path filePath = memoryDir.resolve(filename);
            String frontmatter = buildFrontmatter(entry);
            String body = "\n\n## " + entry.getTitle() + "\n\n" + entry.getContent() + "\n";

            if (Files.exists(filePath)) {
                // 追加到现有文件
                Files.writeString(filePath, body, StandardOpenOption.APPEND);
            } else {
                Files.writeString(filePath, frontmatter + body);
            }

            log.info("[Memory] saved: {} ({})", filename, entry.getAction());
        } catch (IOException e) {
            log.warn("[Memory] failed to save memory entry", e);
        }
    }

    @Override
    public List<ScoredMemory> search(List<String> keywords, SearchContext context, int topK) {
        if (!Files.exists(memoryDir)) {
            return Collections.emptyList();
        }

        List<ScoredMemory> results = new ArrayList<>();
        try (Stream<Path> files = Files.walk(memoryDir, 1)) {
            files.filter(p -> p.toString().endsWith(".md"))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file, StandardCharsets.UTF_8);
                            MemoryEntry entry = parseEntry(content, file);
                            double score = calculateScore(content, entry, keywords, context);
                            if (score > 0) {
                                results.add(new ScoredMemory(entry, score));
                            }
                        } catch (IOException e) {
                            log.warn("[Memory] failed to read file: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("[Memory] failed to walk dir: {}", memoryDir, e);
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    /**
     * 构建 Markdown 文件的 YAML frontmatter。
     */
    private String buildFrontmatter(MemoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(entry.getTitle()).append("\n");
        sb.append("type: memory\n");
        sb.append("date: ").append(LocalDate.now()).append("\n");
        if (entry.getTags() != null && entry.getTags().length > 0) {
            sb.append("tags: [").append(String.join(", ", entry.getTags())).append("]\n");
        }
        if (entry.getThreadPoolId() != null) {
            sb.append("threadPoolId: ").append(entry.getThreadPoolId()).append("\n");
        }
        if (entry.getBusinessType() != null) {
            sb.append("businessType: ").append(entry.getBusinessType()).append("\n");
        }
        sb.append("action: ").append(entry.getAction() != null ? entry.getAction() : "unknown").append("\n");
        sb.append("severity: ").append(entry.getSeverity() != null ? entry.getSeverity() : "medium").append("\n");
        sb.append("---\n");
        return sb.toString();
    }

    /**
     * 从 Markdown 内容解析出 MemoryEntry。
     */
    private MemoryEntry parseEntry(String content, Path file) {
        MemoryEntry entry = new MemoryEntry();
        entry.setContent(content);

        String fileName = file.getFileName().toString().replace(".md", "");
        entry.setTitle(fileName);

        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String yaml = content.substring(3, endIndex).trim();
                for (String line : yaml.split("\n")) {
                    line = line.trim();
                    if (line.contains(": ")) {
                        String key = line.substring(0, line.indexOf(": ")).trim();
                        String value = line.substring(line.indexOf(": ") + 2).trim();
                        switch (key) {
                            case "title" -> entry.setTitle(value);
                            case "threadPoolId" -> entry.setThreadPoolId(value);
                            case "businessType" -> entry.setBusinessType(value);
                            case "action" -> entry.setAction(value);
                            case "severity" -> entry.setSeverity(value);
                            case "tags" -> {
                                String tags = value.replace("[", "").replace("]", "").replace("\"", "");
                                entry.setTags(tags.split(",\s*"));
                            }
                        }
                    }
                }
            }
        }
        return entry;
    }

    /**
     * 加权评分算法，与 FileKnowledgeRetriever 保持一致。
     */
    private double calculateScore(String content, MemoryEntry entry,
                                   List<String> keywords, SearchContext ctx) {
        if (keywords == null || keywords.isEmpty()) return 0;

        String contentLower = content.toLowerCase();
        long matchCount = keywords.stream()
                .filter(q -> q != null && !q.isEmpty())
                .filter(q -> contentLower.contains(q.toLowerCase()))
                .count();
        if (matchCount == 0) return 0;

        double score = (double) matchCount / keywords.size();

        // tags 命中
        if (entry.getTags() != null) {
            boolean tagMatch = Arrays.stream(entry.getTags())
                    .anyMatch(tag -> keywords.stream().anyMatch(k -> tag.toLowerCase().contains(k.toLowerCase())));
            if (tagMatch) score *= 2.0;
        }

        // title 命中
        if (entry.getTitle() != null) {
            boolean titleMatch = keywords.stream().anyMatch(k -> entry.getTitle().toLowerCase().contains(k.toLowerCase()));
            if (titleMatch) score *= 1.8;
        }

        // 上下文提权
        if (ctx.getThreadPoolId() != null && content.contains(ctx.getThreadPoolId()))
            score *= 2.0;
        if (ctx.getBusinessType() != null && content.contains(ctx.getBusinessType()))
            score *= 1.5;

        return score;
    }
}
