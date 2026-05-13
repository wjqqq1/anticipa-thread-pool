package com.baomihuahua.anticipa.agent.knowledge;

import java.time.LocalDate;
import java.util.List;

/**
 * 知识文档 / 记忆条目 模型。
 * <p>
 * 对应 Markdown 文件的 YAML frontmatter + body 结构。
 * 既用于内置知识库，也用于长期记忆。
 * </p>
 */
public class KnowledgeDocument {

    private String title;
    private String type;           // "knowledge" 或 "memory"
    private String category;       // 知识库分类：best-practice / tuning-guide / diagnose
    private List<String> tags;
    private String businessType;   // 对应 BusinessType.Type 名称
    private String priority;       // P0 / P1 / P2
    private String source;         // industry / official / community
    private String threadPoolId;   // 记忆专用：关联的线程池
    private String action;         // 记忆专用：adjust / diagnose / query
    private String severity;       // 记忆专用：low / medium / high
    private LocalDate date;
    private String content;        // Markdown 正文

    /** 搜索匹配得分（运行时填充） */
    private double score;

    public KnowledgeDocument() {
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getThreadPoolId() { return threadPoolId; }
    public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
