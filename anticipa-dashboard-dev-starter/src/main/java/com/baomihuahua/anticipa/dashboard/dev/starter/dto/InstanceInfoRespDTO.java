package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceInfoRespDTO {
    private String instanceId;
    private String appName;
    private String host;
    private String port;
    private String activeProfile;
    private String startTime;
    private String sdkVersion;
}
