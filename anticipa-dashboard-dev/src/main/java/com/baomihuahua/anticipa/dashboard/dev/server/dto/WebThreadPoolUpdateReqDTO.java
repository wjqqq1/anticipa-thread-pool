package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @Min(value = 1, message = "核心线程至少为1")
    @Max(value = 1000, message = "核心线程不能超过1000")
    private Integer corePoolSize;

    @Min(value = 1, message = "最大线程至少为1")
    @Max(value = 1000, message = "最大线程不能超过1000")
    private Integer maximumPoolSize;

    @Min(value = 1, message = "空闲回收至少为1秒")
    @Max(value = 10000, message = "空闲回收不能超过10000秒")
    private Long keepAliveTime;

    @Valid
    private NotifyConfig notify;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfig {

        @Size(max = 64, message = "接收人长度不能超过64字符")
        private String receives;
    }
}
