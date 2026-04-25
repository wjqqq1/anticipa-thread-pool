package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebThreadPoolUpdateReqDTO {

    @NotBlank(message = "命名空间不为空")
    private String namespace;

    @NotBlank(message = "DataId 不为空")
    private String dataId;

    @NotBlank(message = "Group 不为空")
    private String group;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer keepAliveTime;
}
