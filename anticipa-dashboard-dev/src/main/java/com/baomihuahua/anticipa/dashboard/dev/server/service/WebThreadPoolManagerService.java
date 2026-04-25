package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;

import java.util.List;

public interface WebThreadPoolManagerService {

    List<WebThreadPoolDetailRespDTO> listThreadPool(WebThreadPoolListReqDTO requestParam);

    PageDTO<WebThreadPoolDetailRespDTO> listThreadPoolPage(WebThreadPoolListReqDTO requestParam, PageReqDTO pageReq);

    void updateGlobalThreadPool(WebThreadPoolUpdateReqDTO requestParam);
}
