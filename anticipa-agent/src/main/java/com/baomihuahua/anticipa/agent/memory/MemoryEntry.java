package com.baomihuahua.anticipa.agent.memory;

/**
 * 记忆条目模型。
 * <p>
 * 对应 Markdown 文件的 YAML frontmatter + body 结构，
 * 表示一条长期记忆，用于跨会话经验积累。
 * </p>
 */
public class MemoryEntry {
    private String title;
    private String content;
    private String threadPoolId;
    private String businessType;
    private String action;       // adjust / diagnose / query
    private String severity;     // low / medium / high
    private String[] tags;
    private long timestamp;

    public MemoryEntry() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getThreadPoolId() { return threadPoolId; }
    public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
