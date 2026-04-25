package com.baomihuahua.anticipa.core.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警限流器
 * <p>
 * 用于控制同一线程池在同一告警周期内不会重复发送报警信息
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class AlarmRateLimiter {

    private final Map<String, Long> lastNotifyTimeMap = new ConcurrentHashMap<>();

    /**
     * 是否允许发送报警
     *
     * @param threadPoolId 线程池唯一标识
     * @param intervalMin  告警间隔（分钟）
     * @return true 允许发送，false 不允许发送
     */
    public boolean tryAcquire(String threadPoolId, int intervalMin) {
        Long lastNotifyTime = lastNotifyTimeMap.get(threadPoolId);
        long currentTime = System.currentTimeMillis();

        if (lastNotifyTime == null) {
            lastNotifyTimeMap.put(threadPoolId, currentTime);
            return true;
        }

        long intervalMillis = intervalMin * 60 * 1000L;
        if (currentTime - lastNotifyTime >= intervalMillis) {
            lastNotifyTimeMap.put(threadPoolId, currentTime);
            return true;
        }

        return false;
    }
}
