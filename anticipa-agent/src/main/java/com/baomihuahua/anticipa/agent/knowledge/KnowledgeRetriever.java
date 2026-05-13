package com.baomihuahua.anticipa.agent.knowledge;

import java.util.List;

/**
 * 知识库 / 记忆 检索器接口。
 */
public interface KnowledgeRetriever {

    /**
     * 根据关键词、上下文搜索相关文档。
     *
     * @param queries   关键词列表
     * @param context   搜索上下文（含 threadPoolId、businessType 等）
     * @param topK      返回前 K 条
     * @return 按得分降序排列的文档列表
     */
    List<KnowledgeDocument> search(List<String> queries, SearchContext context, int topK);

    /**
     * 搜索上下文。
     */
    class SearchContext {
        private String threadPoolId;
        private String businessType;
        private String userMessage;

        public String getThreadPoolId() { return threadPoolId; }
        public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
        public String getBusinessType() { return businessType; }
        public void setBusinessType(String businessType) { this.businessType = businessType; }
        public String getUserMessage() { return userMessage; }
        public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    }
}
