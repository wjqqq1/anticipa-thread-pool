package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebThreadPoolListReqDTO {

    private String namespace;

    private String serviceName;
}
