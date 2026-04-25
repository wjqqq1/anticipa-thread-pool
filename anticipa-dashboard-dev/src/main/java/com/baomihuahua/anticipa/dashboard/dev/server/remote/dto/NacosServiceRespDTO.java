package com.baomihuahua.anticipa.dashboard.dev.server.remote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NacosServiceRespDTO {

    private String ip;

    private Integer port;

    private String serviceName;

    private String clusterName;
}
