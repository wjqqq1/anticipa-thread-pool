package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolUpdateReqDTO;

import java.util.List;

public interface ThreadPoolManagerService {

    /**
     * 查询线程池集合
     *
     * @param requestParam 请求参数
     * @return 线程池集合
     */
    List<ThreadPoolDetailRespDTO> listThreadPool(ThreadPoolListReqDTO requestParam);

    /**
     * 分页查询线程池集合
     *
     * @param requestParam 请求参数
     * @param pageReq      分页参数
     * @return 线程池分页结果
     */
    PageDTO<ThreadPoolDetailRespDTO> listThreadPoolPage(ThreadPoolListReqDTO requestParam, PageReqDTO pageReq);

    /**
     * 全局修改线程池参数
     *
     * @param requestParam 请求参数
     */
    void updateGlobalThreadPool(ThreadPoolUpdateReqDTO requestParam);
}
