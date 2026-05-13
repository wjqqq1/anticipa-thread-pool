package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;

import java.util.List;

public interface WebThreadPoolManagerService {

    /**
     * 查询线程池集合
     *
     * @param requestParam 请求参数
     * @return 线程池集合
     */
    List<WebThreadPoolDetailRespDTO> listThreadPool(WebThreadPoolListReqDTO requestParam);

    /**
     * 分页查询线程池集合
     *
     * @param requestParam 请求参数
     * @param pageReq      分页参数
     * @return 线程池分页结果
     */
    PageDTO<WebThreadPoolDetailRespDTO> listThreadPoolPage(WebThreadPoolListReqDTO requestParam, PageReqDTO pageReq);

    /**
     * 全局修改线程池参数
     *
     * @param requestParam 请求参数
     */
    void updateGlobalThreadPool(WebThreadPoolUpdateReqDTO requestParam);
}
