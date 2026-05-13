package com.baomihuahua.anticipa.agent.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 知识库服务。
 * <p>
 * 负责内置知识库的初始化（从 classpath 释放到文件系统）、
 * 以及知识检索的统一入口。
 * </p>
 */
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final FileKnowledgeRetriever retriever;
    private final String knowledgeDir;

    public KnowledgeService(FileKnowledgeRetriever retriever, String knowledgeDir) {
        this.retriever = retriever;
        this.knowledgeDir = knowledgeDir;
    }

    /**
     * 首次运行时，将 JAR 内 builtin-knowledge/ 下的文档释放到文件系统。
     */
    public void initBuiltinKnowledge() {
        Path targetDir = Paths.get(knowledgeDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            log.warn("[Knowledge] failed to create knowledge dir: {}", knowledgeDir, e);
            return;
        }

        // 从 classpath 读取内置知识库文件列表
        String[] builtinFiles = {
                "best-practice-cpu-intensive.md",
                "best-practice-io-intensive.md",
                "best-practice-mixed.md",
                "best-practice-strategy-rejected.md",
                "best-practice-strategy-queue.md",
                "best-practice-monitoring.md",
                "best-practice-common-pitfalls.md",
                "tuning-guide-core-size.md",
                "tuning-guide-queue-size.md",
                "diagnose-high-cpu.md",
                "diagnose-task-rejection.md",
                "diagnose-uneven-load.md"
        };

        for (String filename : builtinFiles) {
            Path targetFile = targetDir.resolve(filename);
            if (Files.exists(targetFile)) {
                continue; // 已存在则跳过
            }
            try (InputStream in = getClass().getClassLoader()
                    .getResourceAsStream("builtin-knowledge/" + filename)) {
                if (in == null) {
                    log.warn("[Knowledge] builtin file not found in classpath: {}", filename);
                    continue;
                }
                Files.copy(in, targetFile);
                log.info("[Knowledge] initialized builtin knowledge: {}", filename);
            } catch (IOException e) {
                log.warn("[Knowledge] failed to copy builtin file: {}", filename, e);
            }
        }
    }

    /**
     * 搜索知识库和记忆库。
     */
    public List<KnowledgeDocument> search(List<String> queries, KnowledgeRetriever.SearchContext context, int topK) {
        return retriever.search(queries, context, topK);
    }

    /**
     * 从用户消息中提取关键词。
     */
    public List<String> extractKeywords(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Collections.emptyList();
        }
        // 简单的关键词提取：去停用词，保留有意义的词
        Set<String> stopWords = Set.of("的", "了", "在", "是", "我", "有", "和", "就", "不",
                "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
                "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她", "它",
                "们", "那", "什么", "怎么", "为什么", "如何", "请", "帮我", "可以",
                "a", "an", "the", "is", "are", "was", "were", "to", "of", "in",
                "for", "on", "with", "at", "by", "from", "and", "or", "but");

        return Arrays.stream(userMessage.split("[\\s,，、。.！？!?；;：:]+"))
                .filter(w -> w.length() >= 2)
                .filter(w -> !stopWords.contains(w.toLowerCase()))
                .distinct()
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
    }
}
