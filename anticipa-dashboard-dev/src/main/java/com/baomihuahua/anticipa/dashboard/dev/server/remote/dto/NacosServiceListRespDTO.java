package com.baomihuahua.anticipa.dashboard.dev.server.remote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NacosServiceListRespDTO {

    private Integer count;

    private List<NacosServiceRespDTO> serviceList;
}
