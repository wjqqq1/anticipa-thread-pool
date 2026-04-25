package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolUpdateReqDTO;

import java.util.List;

public interface ThreadPoolManagerService {

    List<ThreadPoolDetailRespDTO> listThreadPool(ThreadPoolListReqDTO requestParam);

    PageDTO<ThreadPoolDetailRespDTO> listThreadPoolPage(ThreadPoolListReqDTO requestParam, PageReqDTO pageReq);

    void updateGlobalThreadPool(ThreadPoolUpdateReqDTO requestParam);
}
