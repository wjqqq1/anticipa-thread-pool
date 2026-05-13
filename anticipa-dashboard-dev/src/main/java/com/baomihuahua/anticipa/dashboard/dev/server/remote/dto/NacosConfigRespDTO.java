package com.baomihuahua.anticipa.dashboard.dev.server.remote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NacosConfigRespDTO {

    private String id;

    private String dataId;

    private String group;

    private String tenant;

    private String appName;

    private String type;

    private String content;

    private String md5;

    private Long lastModified;
}
