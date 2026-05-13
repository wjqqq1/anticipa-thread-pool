package com.baomihuahua.anticipa.core.notification.service;

import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.dto.WebThreadPoolConfigChangeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 通知分发器
 * <p>
 * 将报警、配置变更等通知分发给所有注册的通知服务
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class NotifierDispatcher {

    private final List<NotifierService> notifierServices;

    private final AlarmRateLimiter alarmRateLimiter;

    /**
     * 发送报警通知
     *
     * @param alarmDTO 报警通知数据
     * @return 是否实际进入发送流程（已通过限流并调用了通知实现）；无渠道或限流未通过时为 false
     */
    public boolean sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO) {
        // 先检查渠道再限流：若先 tryAcquire 再发现没有 NotifierService，会白白占用限流槽位，
        // 导致同一线程池在整段间隔内都无法再发钉钉（表现为「一次都没成功」）。
        if (notifierServices.isEmpty()) {
            log.warn("Alarm notification skipped: no NotifierService beans "
                    + "(check anticipa.rejected-analysis.ding-talk.enabled=true and webhook-url)");
            return false;
        }
        String rateLimitKey = resolveAlarmRateLimitKey(alarmDTO);
        int intervalMin = alarmDTO.getInterval() != null ? alarmDTO.getInterval() : 1;
        if (!alarmRateLimiter.tryAcquire(rateLimitKey, intervalMin)) {
            log.debug("Alarm notification skipped (rate limit), key={}, intervalMin={}", rateLimitKey, intervalMin);
            return false;
        }
        ThreadPoolAlarmNotifyDTO alarm = alarmDTO.getSupplier() != null
                ? alarmDTO.getSupplier().get()
                : alarmDTO;
        for (NotifierService notifierService : notifierServices) {
            try {
                notifierService.sendAlarmMessage(alarm);
            } catch (Exception e) {
                log.error("Failed to send alarm message via {}", notifierService.getClass().getSimpleName(), e);
            }
        }
        return true;
    }

    /**
     * 默认同一线程池共用一个限流桶；定时任务 {@code SCHEDULED_TASK_ANALYSIS} 单独一桶，
     * 避免 ThreadPoolAlarmChecker 等高频告警把定时报告挤掉。
     */
    private static String resolveAlarmRateLimitKey(ThreadPoolAlarmNotifyDTO alarmDTO) {
        String poolId = alarmDTO.getThreadPoolId();
        String base = (poolId != null && !poolId.isBlank()) ? poolId : "_";
        if ("SCHEDULED_TASK_ANALYSIS".equals(alarmDTO.getAlarmType())) {
            return base + ":SCHEDULED_TASK";
        }
        return base;
    }

    /**
     * 发送配置变更通知
     *
     * @param changeDTO 配置变更通知数据
     */
    public void sendChangeMessage(ThreadPoolConfigChangeDTO changeDTO) {
        for (NotifierService notifierService : notifierServices) {
            try {
                notifierService.sendChangeMessage(changeDTO);
            } catch (Exception e) {
                log.error("Failed to send change message via {}", notifierService.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 发送 Web 线程池配置变更通知
     *
     * @param changeDTO Web 线程池配置变更通知数据
     */
    public void sendWebChangeMessage(WebThreadPoolConfigChangeDTO changeDTO) {
        for (NotifierService notifierService : notifierServices) {
            try {
                notifierService.sendWebChangeMessage(changeDTO);
            } catch (Exception e) {
                log.error("Failed to send web change message via {}", notifierService.getClass().getSimpleName(), e);
            }
        }
    }
}
