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
     */
    public void sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO) {
        if (alarmRateLimiter.tryAcquire(alarmDTO.getThreadPoolId(), alarmDTO.getInterval())) {
            // 加载延迟数据
            ThreadPoolAlarmNotifyDTO alarm = alarmDTO.getSupplier().get();
            for (NotifierService notifierService : notifierServices) {
                try {
                    notifierService.sendAlarmMessage(alarm);
                } catch (Exception e) {
                    log.error("Failed to send alarm message via {}", notifierService.getClass().getSimpleName(), e);
                }
            }
        }
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
