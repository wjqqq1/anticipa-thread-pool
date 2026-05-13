package com.baomihuahua.anticipa.agent.approval;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ApprovalRequest {
    private String requestId;
    private long timestamp;
    private String userId;
    private String toolName;
    private Map<String, Object> params;
    private String riskLevel;
    private List<String> warnings;
    /** 审批标题，如"调整 producer 线程池配置" */
    private String title;
    /** 目标服务名 */
    private String serviceName;
    /** AI 分析推理过程 */
    private String reasoning;
}
