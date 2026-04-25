package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ProjectInfoRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/api/anticipa-dashboard/project/list")
    public Result<List<ProjectInfoRespDTO>> listProject() {
        return Result.success(projectService.listProject());
    }

    @GetMapping("/api/projects")
    public Result<PageDTO<ProjectInfoRespDTO>> listProjectPage(PageReqDTO pageReq) {
        return Result.success(projectService.listProjectPage(pageReq));
    }
}
