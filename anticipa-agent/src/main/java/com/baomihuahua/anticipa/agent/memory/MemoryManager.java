package com.baomihuahua.anticipa.agent.memory;

import com.baomihuahua.anticipa.agent.AgentLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆管理器。
 * <p>
 * 管理短期记忆（当前会话）和长期记忆（跨会话，文件持久化）。
 * </p>
 * <p>
 * 短期记忆：当前会话的上下文和关注点列表。
 * 长期记忆：通过 FileMemoryStore 持久化到 Markdown 文件，
 * 每次调优/诊断/告警分析完成后写入，每轮 LLM 调用前检索。
 * </p>
 */
public class MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    private final MemoryStore memoryStore;

    /** 短期记忆：会话 ID → 上下文列表 */
    private final Map<String, List<String>> shortTermContexts = new HashMap<>();

    /** 短期记忆：会话 ID → 关注的 threadPoolId 列表 */
    private final Map<String, Set<String>> sessionThreadPools = new HashMap<>();

    public MemoryManager(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    // ─── 短期记忆 ───

    /**
     * 记录当前会话关注的线程池。
     */
    public void recordThreadPool(String sessionId, String threadPoolId) {
        sessionThreadPools.computeIfAbsent(sessionId, k -> new HashSet<>()).add(threadPoolId);
    }

    /**
     * 获取当前会话关注的线程池列表。
     */
    public Set<String> getSessionThreadPools(String sessionId) {
        return sessionThreadPools.getOrDefault(sessionId, Collections.emptySet());
    }

    /**
     * 添加短期上下文信息。
     */
    public void addContext(String sessionId, String context) {
        shortTermContexts.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(context);
    }

    /**
     * 获取短期上下文摘要。
     */
    public String getShortTermSummary(String sessionId) {
        List<String> contexts = shortTermContexts.get(sessionId);
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }
        return contexts.stream()
                .limit(5)
                .collect(Collectors.joining("\n"));
    }

    // ─── 长期记忆 ───

    /**
     * 保存记忆条目到长期存储。
     */
    public void saveMemory(MemoryEntry entry) {
        memoryStore.save(entry);
        log.debug("[Memory] saved memory: {}", entry.getTitle());
    }

    /**
     * 创建并保存一条调优相关的记忆。
     */
    public void recordAdjustment(String threadPoolId, String businessType,
                                  String summary, String detail) {
        MemoryEntry entry = new MemoryEntry();
        entry.setTitle(threadPoolId + " 调优记录");
        entry.setContent("## 调整内容\n" + summary + "\n\n## 详情\n" + detail);
        entry.setThreadPoolId(threadPoolId);
        entry.setBusinessType(businessType);
        entry.setAction("adjust");
        entry.setSeverity("medium");
        entry.setTags(new String[]{"调优", threadPoolId});
        entry.setTimestamp(System.currentTimeMillis());
        memoryStore.save(entry);
    }

    /**
     * 创建并保存一条诊断相关的记忆。
     */
    public void recordDiagnosis(String threadPoolId, String businessType,
                                 String problem, String conclusion) {
        MemoryEntry entry = new MemoryEntry();
        entry.setTitle(threadPoolId + " 诊断记录");
        entry.setContent("## 问题\n" + problem + "\n\n## 结论\n" + conclusion);
        entry.setThreadPoolId(threadPoolId);
        entry.setBusinessType(businessType);
        entry.setAction("diagnose");
        entry.setSeverity("high");
        entry.setTags(new String[]{"诊断", threadPoolId});
        entry.setTimestamp(System.currentTimeMillis());
        memoryStore.save(entry);
    }

    /**
     * 搜索相关长期记忆，返回格式化的文本（用于注入 System Prompt）。
     */
    public String searchMemoryText(List<String> keywords, String threadPoolId, String businessType) {
        MemoryStore.SearchContext ctx = new MemoryStore.SearchContext();
        ctx.setThreadPoolId(threadPoolId);
        ctx.setBusinessType(businessType);

        List<ScoredMemory> results = memoryStore.search(keywords, ctx, 3);
        if (results.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ScoredMemory sm : results) {
            MemoryEntry e = sm.getEntry();
            sb.append("- ").append(e.getTitle());
            if (e.getSeverity() != null) {
                sb.append(" [").append(e.getSeverity()).append("]");
            }
            sb.append("\n");
            // 截取正文前 300 字作为摘要
            String content = e.getContent();
            if (content != null) {
                int bodyStart = content.indexOf("---", content.indexOf("---") + 3);
                if (bodyStart > 0) {
                    content = content.substring(bodyStart + 3).trim();
                }
                if (content.length() > 300) {
                    content = content.substring(0, 300) + "...\n";
                }
                sb.append("  ").append(content.replace("\n", "\n  ")).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 清理会话的短期记忆。
     */
    public void clearSession(String sessionId) {
        shortTermContexts.remove(sessionId);
        sessionThreadPools.remove(sessionId);
    }
}
