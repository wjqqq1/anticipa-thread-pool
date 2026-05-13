package com.baomihuahua.anticipa.agent.context;

import com.baomihuahua.anticipa.agent.AgentLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 上下文管理器。
 * <p>
 * 参考 Claude Code 五阶段压缩流水线，按成本从低到高依次执行：
 * Stage 1: Tool Result Budget（工具结果裁剪）
 * Stage 2: Snip Compact（早期消息丢弃）
 * Stage 3: Microcompact（精准清理）
 * Stage 4: Context Collapse（上下文坍缩 — 生成摘要）
 * Stage 5: Auto-Compact（自动压缩 — 达到阈值时强制触发）
 * </p>
 */
public class ContextManager {

    private static final Logger log = LoggerFactory.getLogger(ContextManager.class);

    /** 最大 Token 预算 */
    private final int maxTokenBudget;
    /** 触发压缩的阈值（剩余 Token 低于此值触发） */
    private final int compactThreshold;
    /** 单个工具结果最大 Token */
    private final int toolResultMaxTokens;
    /** 连续失败计数（避免死循环） */
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** 简易 Token 估算：每字符约 0.25 token */
    private static final double TOKENS_PER_CHAR = 0.25;

    public ContextManager(int maxTokenBudget, int compactThreshold, int toolResultMaxTokens) {
        this.maxTokenBudget = maxTokenBudget;
        this.compactThreshold = compactThreshold;
        this.toolResultMaxTokens = toolResultMaxTokens;
    }

    /**
     * 每次调用 LLM 前执行，确保消息列表在 Token 预算内。
     */
    public List<AgentLoop.Message> prepareMessages(List<AgentLoop.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int currentTokens = estimateTokens(messages);
        if (currentTokens <= maxTokenBudget) {
            consecutiveFailures = 0;
            return messages;
        }

        List<AgentLoop.Message> result = new ArrayList<>(messages);

        // Stage 1: 工具结果裁剪
        result = trimToolResults(result);
        if (withinBudget(result)) {
            consecutiveFailures = 0;
            return result;
        }

        // Stage 2: 早期消息丢弃
        result = snipOldMessages(result);
        if (withinBudget(result)) {
            consecutiveFailures = 0;
            return result;
        }

        // Stage 3: 精准清理
        result = microcompact(result);
        if (withinBudget(result)) {
            consecutiveFailures = 0;
            return result;
        }

        // Stage 4: 上下文坍缩（摘要生成当前不做 LLM 调用，采用截断策略）
        result = collapseContext(result);
        if (withinBudget(result)) {
            consecutiveFailures = 0;
            return result;
        }

        // 如果还是超限，强制丢弃
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            log.warn("[Context] compact failed after {} attempts, force truncating", consecutiveFailures);
            consecutiveFailures = 0;
            return forceTruncate(result);
        }

        return result;
    }

    /**
     * 估算消息列表的 Token 数。
     */
    public int estimateTokens(List<AgentLoop.Message> messages) {
        int totalChars = 0;
        for (AgentLoop.Message msg : messages) {
            totalChars += msg.getContent() != null ? msg.getContent().length() : 0;
        }
        return (int) (totalChars * TOKENS_PER_CHAR);
    }

    // ─── Stage 1: 工具结果裁剪 ───

    private List<AgentLoop.Message> trimToolResults(List<AgentLoop.Message> messages) {
        List<AgentLoop.Message> result = new ArrayList<>();
        for (AgentLoop.Message msg : messages) {
            if ("tool".equals(msg.getRole()) && msg.getContent() != null) {
                int tokens = estimateTokens(List.of(msg));
                if (tokens > toolResultMaxTokens) {
                    int maxChars = (int) (toolResultMaxTokens / TOKENS_PER_CHAR);
                    String trimmed = msg.getContent().substring(0, Math.min(maxChars, msg.getContent().length()));
                    trimmed += "\n...（结果已截断，共 " + tokens + " tokens）";
                    AgentLoop.Message trimmedMsg = new AgentLoop.Message(msg.getRole(), trimmed);
                    trimmedMsg.setToolCallId(msg.getToolCallId());
                    result.add(trimmedMsg);
                    continue;
                }
            }
            result.add(msg);
        }
        return result;
    }

    // ─── Stage 2: 早期消息丢弃 ───

    private List<AgentLoop.Message> snipOldMessages(List<AgentLoop.Message> messages) {
        // 保留 system prompt 和最近 10 轮对话
        List<AgentLoop.Message> result = new ArrayList<>();
        boolean systemFound = false;
        for (AgentLoop.Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                result.add(msg);
                systemFound = true;
                break;
            }
        }
        if (!systemFound && !messages.isEmpty()) {
            result.add(messages.get(0)); // 第一条当 system
        }

        // 取最后 N 条消息（保留最近 10 轮 = 20 条消息）
        int keepCount = Math.min(20, messages.size());
        int startIdx = Math.max(0, messages.size() - keepCount);
        // 向前调整：确保不从 assistant(tool_calls) 和 tool 消息之间截断
        if (startIdx > 0 && startIdx < messages.size()) {
            AgentLoop.Message firstMsg = messages.get(startIdx);
            if ("tool".equals(firstMsg.getRole())) {
                // 向前查找对应的 assistant(tool_calls) 消息
                for (int k = startIdx - 1; k >= 0; k--) {
                    AgentLoop.Message prev = messages.get(k);
                    if ("assistant".equals(prev.getRole()) && prev.getToolCalls() != null
                            && !prev.getToolCalls().isEmpty()) {
                        startIdx = k;
                        break;
                    }
                    // 如果遇到非 tool/assistant 消息，说明这个 tool 是孤立的，不需要向前扩展
                    if (!"tool".equals(prev.getRole())) {
                        break;
                    }
                }
            }
        }
        // 确保包含 system
        for (int i = startIdx; i < messages.size(); i++) {
            AgentLoop.Message msg = messages.get(i);
            if (!"system".equals(msg.getRole())) {
                result.add(msg);
            }
        }
        return result;
    }

    // ─── Stage 3: 精准清理 ───

    private List<AgentLoop.Message> microcompact(List<AgentLoop.Message> messages) {
        List<AgentLoop.Message> result = new ArrayList<>();

        int i = 0;
        while (i < messages.size()) {
            AgentLoop.Message msg = messages.get(i);

            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                // assistant(tool_calls) 消息 — 收集紧随其后的所有 tool 消息
                List<AgentLoop.Message> toolMsgs = new ArrayList<>();
                int j = i + 1;
                while (j < messages.size() && "tool".equals(messages.get(j).getRole())) {
                    toolMsgs.add(messages.get(j));
                    j++;
                }

                if (toolMsgs.size() == msg.getToolCalls().size()) {
                    // tool 消息数量匹配 — 完整保留序列，但截断 tool 内容
                    result.add(msg); // assistant(tool_calls) 原样保留
                    for (AgentLoop.Message toolMsg : toolMsgs) {
                        String content = toolMsg.getContent();
                        if (content != null && content.length() > 100) {
                            AgentLoop.Message truncated = new AgentLoop.Message("tool", content.substring(0, 100) + "...");
                            truncated.setToolCallId(toolMsg.getToolCallId());
                            result.add(truncated);
                        } else {
                            result.add(toolMsg);
                        }
                    }
                } else {
                    // tool 消息数量不匹配（压缩导致丢失）— 整个序列转为 user 摘要
                    StringBuilder summary = new StringBuilder("[工具调用摘要] ");
                    for (AgentLoop.ToolCall tc : msg.getToolCalls()) {
                        summary.append(tc.getToolName()).append("(").append(tc.getParams()).append(")");
                    }
                    for (AgentLoop.Message toolMsg : toolMsgs) {
                        String content = toolMsg.getContent();
                        if (content != null && content.length() > 80) {
                            content = content.substring(0, 80) + "...";
                        }
                        summary.append(" → ").append(content);
                    }
                    result.add(new AgentLoop.Message("user", summary.toString()));
                }
                i = j; // 跳过已处理的 tool 消息
            } else if ("tool".equals(msg.getRole())) {
                // 孤立的 tool 消息（没有前导 assistant(tool_calls)）— 转为 user
                String content = msg.getContent();
                if (content != null && content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                result.add(new AgentLoop.Message("user", "[工具结果] " + content));
                i++;
            } else {
                // 其他消息原样保留
                result.add(msg);
                i++;
            }
        }
        return result;
    }

    // ─── Stage 4: 上下文坍缩 ───

    private List<AgentLoop.Message> collapseContext(List<AgentLoop.Message> messages) {
        List<AgentLoop.Message> result = new ArrayList<>();

        // 保留 system prompt
        for (AgentLoop.Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                result.add(msg);
                break;
            }
        }

        // 保留最后 3 轮对话
        int keepCount = Math.min(6, messages.size());
        int startIdx = Math.max(0, messages.size() - keepCount);
        // 向前调整：避免在 assistant(tool_calls) 和 tool 之间截断
        if (startIdx > 0 && startIdx < messages.size()) {
            AgentLoop.Message firstMsg = messages.get(startIdx);
            if ("tool".equals(firstMsg.getRole())) {
                for (int k = startIdx - 1; k >= 0; k--) {
                    AgentLoop.Message prev = messages.get(k);
                    if ("assistant".equals(prev.getRole()) && prev.getToolCalls() != null
                            && !prev.getToolCalls().isEmpty()) {
                        startIdx = k;
                        break;
                    }
                    if (!"tool".equals(prev.getRole())) {
                        break;
                    }
                }
            }
        }

        for (int i = startIdx; i < messages.size(); i++) {
            AgentLoop.Message msg = messages.get(i);
            if (!"system".equals(msg.getRole())) {
                if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                    String content = msg.getContent();
                    if (content != null && content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    AgentLoop.Message copy = new AgentLoop.Message(msg.getRole(), content);
                    copy.setToolCalls(msg.getToolCalls());
                    result.add(copy);
                } else if ("tool".equals(msg.getRole())) {
                    // tool 消息保留 toolCallId，截断内容
                    String content = msg.getContent();
                    if (content != null && content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    AgentLoop.Message copy = new AgentLoop.Message("tool", content);
                    copy.setToolCallId(msg.getToolCallId());
                    result.add(copy);
                } else {
                    result.add(msg);
                }
            }
        }

        // 添加坍缩说明
        if (messages.size() > result.size()) {
            result.add(new AgentLoop.Message("system",
                    "（上下文已压缩。原始对话共 " + messages.size() + " 条消息，"
                            + "已折叠为摘要，保留了最近的 " + keepCount + " 条。）"));
        }

        return result;
    }

    // ─── 强制截断 ───

    private List<AgentLoop.Message> forceTruncate(List<AgentLoop.Message> messages) {
        List<AgentLoop.Message> result = new ArrayList<>();
        int totalChars = 0;
        int maxChars = (int) (maxTokenBudget / TOKENS_PER_CHAR);

        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentLoop.Message msg = messages.get(i);
            int msgChars = msg.getContent() != null ? msg.getContent().length() : 0;
            if (totalChars + msgChars > maxChars) {
                if ("system".equals(msg.getRole())) {
                    // system 必须保留，截断内容
                    String truncated = msg.getContent().substring(0, Math.min(500, msg.getContent().length()));
                    result.add(0, new AgentLoop.Message(msg.getRole(), truncated + "..."));
                    totalChars += 500;
                }
                // 非 system 消息直接丢弃
                continue;
            }
            result.add(0, msg);
            totalChars += msgChars;
        }
        return result;
    }

    private boolean withinBudget(List<AgentLoop.Message> messages) {
        return estimateTokens(messages) <= maxTokenBudget;
    }
}
