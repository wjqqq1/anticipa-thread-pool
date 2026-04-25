package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolStateRespDTO;

import java.util.List;

public interface ThreadPoolInstanceService {

    List<ThreadPoolBaseMetricsRespDTO> listBasicMetrics(String namespace, String serviceName, String threadPoolId);

    ThreadPoolStateRespDTO getRuntimeState(String threadPoolId, String networkAddress);
}
