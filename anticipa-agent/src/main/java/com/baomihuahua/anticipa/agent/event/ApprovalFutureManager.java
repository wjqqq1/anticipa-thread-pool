package com.baomihuahua.anticipa.agent.event;

import com.baomihuahua.anticipa.agent.approval.ApprovalDecision;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理流式场景下的审批 Future 注册与解析。
 * <p>
 * 当流式 AgentLoop 需要审批时，创建一个 {@link CompletableFuture} 并注册到此管理器。
 * 后端的 /approval/stream 端点通过 requestId 查找到对应的 Future 并 complete() 它，
 * 从而解开 AgentLoop 的阻塞等待。
 * </p>
 * <p>
 * 由 {@link com.baomihuahua.anticipa.agent.config.AgentAutoConfiguration} 统一注册为 Bean，
 * 不使用 @Component 以避免与 auto-configuration 的 @ConditionalOnMissingBean 冲突。
 * </p>
 */
public class ApprovalFutureManager {

    private final Map<String, CompletableFuture<ApprovalDecision>> pending = new ConcurrentHashMap<>();

    /**
     * 注册一个审批等待 Future 并返回。
     * AgentLoop 线程调用 future.get() 阻塞，等待审批端点 resolve。
     */
    public CompletableFuture<ApprovalDecision> register(String requestId) {
        CompletableFuture<ApprovalDecision> future = new CompletableFuture<>();
        pending.put(requestId, future);
        return future;
    }

    /**
     * 解除审批阻塞：由审批端点调用，complete Future 唤醒 AgentLoop 线程。
     *
     * @return true 表示成功找到并完成，false 表示无匹配的 pending 请求
     */
    public boolean resolve(String requestId, ApprovalDecision decision) {
        CompletableFuture<ApprovalDecision> future = pending.remove(requestId);
        if (future != null) {
            future.complete(decision);
            return true;
        }
        return false;
    }

    /**
     * 移除已超时或取消的审批 Future。
     */
    public void remove(String requestId) {
        pending.remove(requestId);
    }
}
