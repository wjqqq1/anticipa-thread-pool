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
public class ThreadPoolListReqDTO {

    private String namespace;

    private String serviceName;
}
