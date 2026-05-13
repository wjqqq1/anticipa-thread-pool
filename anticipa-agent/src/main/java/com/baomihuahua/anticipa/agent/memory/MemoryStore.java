package com.baomihuahua.anticipa.agent.memory;

import java.util.List;

/**
 * 记忆存储接口。
 * <p>
 * 将长期记忆持久化到文件系统（Markdown 文件）。
 * </p>
 */
public interface MemoryStore {

    /**
     * 保存一条记忆条目到持久化存储。
     */
    void save(MemoryEntry entry);

    /**
     * 根据关键词搜索相关记忆。
     *
     * @param keywords 关键词列表
     * @param context  搜索上下文
     * @param topK     返回前 K 条
     * @return 带得分的记忆列表
     */
    List<ScoredMemory> search(List<String> keywords, SearchContext context, int topK);

    /**
     * 搜索上下文。
     */
    class SearchContext {
        private String threadPoolId;
        private String businessType;

        public String getThreadPoolId() { return threadPoolId; }
        public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
    }
}
