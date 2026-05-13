package com.baomihuahua.anticipa.dashboard.dev.server.remote.dto;

import cn.hutool.core.collection.CollUtil;
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

    /**
     * 服务实例数量
     */
    private Integer count;

    /**
     * 服务实例明细（Nacos catalog/instances 接口返回 list 字段）
     */
    private List<NacosServiceRespDTO> list;

    /**
     * 适配 Nacos 跨版本之间参数值变更（部分版本返回 hosts 字段）
     */
    private List<NacosServiceRespDTO> serviceList;

    /**
     * 兼容获取服务实例列表：优先 list，其次 serviceList/hosts
     */
    public List<NacosServiceRespDTO> getServiceList() {
        if (CollUtil.isNotEmpty(list)) {
            return list;
        }
        return serviceList;
    }
}
