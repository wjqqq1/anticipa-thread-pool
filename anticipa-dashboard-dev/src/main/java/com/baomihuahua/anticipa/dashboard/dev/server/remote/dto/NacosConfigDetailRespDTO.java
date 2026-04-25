package com.baomihuahua.anticipa.dashboard.dev.server.remote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NacosConfigDetailRespDTO {

    private String content;

    private String dataId;

    private String group;

    private String id;

    private String md5;

    private String appName;
}
