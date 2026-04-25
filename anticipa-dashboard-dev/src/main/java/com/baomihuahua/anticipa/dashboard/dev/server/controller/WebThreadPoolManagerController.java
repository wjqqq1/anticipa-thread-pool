package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WebThreadPoolManagerController {

    private final WebThreadPoolManagerService webThreadPoolManagerService;

    @GetMapping("/api/anticipa-dashboard/web/thread-pool/list")
    public Result<List<WebThreadPoolDetailRespDTO>> listThreadPool(@Valid WebThreadPoolListReqDTO requestParam) {
        return Result.success(webThreadPoolManagerService.listThreadPool(requestParam));
    }

    @GetMapping("/api/web/thread-pools")
    public Result<PageDTO<WebThreadPoolDetailRespDTO>> listThreadPoolPage(@Valid WebThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        return Result.success(webThreadPoolManagerService.listThreadPoolPage(requestParam, pageReq));
    }

    @PostMapping("/api/anticipa-dashboard/web/thread-pool/update")
    public Result<Void> updateGlobalThreadPool(@RequestBody WebThreadPoolUpdateReqDTO requestParam) {
        webThreadPoolManagerService.updateGlobalThreadPool(requestParam);
        return Result.success();
    }
}
