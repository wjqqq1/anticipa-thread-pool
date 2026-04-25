package com.baomihuahua.anticipa.dashboard.dev.server.service;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ProjectInfoRespDTO;

import java.util.List;

public interface ProjectService {

    List<ProjectInfoRespDTO> listProject();

    PageDTO<ProjectInfoRespDTO> listProjectPage(PageReqDTO pageReq);
}
