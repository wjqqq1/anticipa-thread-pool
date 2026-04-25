package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;

import java.util.List;

public interface WebThreadPoolInstanceService {

    List<WebThreadPoolBaseMetricsRespDTO> listBasicMetrics(String namespace, String serviceName);

    WebThreadPoolStateRespDTO getRuntimeState(String networkAddress);
}
