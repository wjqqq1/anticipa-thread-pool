package com.baomihuahua.anticipa.core.notification.service;

import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.dto.WebThreadPoolConfigChangeDTO;

/**
 * 通知服务接口
 * <p>
 * 所有通知服务的顶层接口，用于发送报警和配置变更通知
 * </p>
 */
public interface NotifierService {

    /**
     * 发送报警通知
     *
     * @param alarmDTO 报警通知数据
     */
    void sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO);

    /**
     * 发送配置变更通知
     *
     * @param changeDTO 配置变更通知数据
     */
    void sendChangeMessage(ThreadPoolConfigChangeDTO changeDTO);

    /**
     * 发送 Web 线程池配置变更通知
     *
     * @param changeDTO Web 线程池配置变更通知数据
     */
    void sendWebChangeMessage(WebThreadPoolConfigChangeDTO changeDTO);
}
