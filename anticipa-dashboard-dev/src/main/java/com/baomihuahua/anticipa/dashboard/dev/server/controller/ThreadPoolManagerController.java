package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ThreadPoolManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ThreadPoolManagerController {

    private final ThreadPoolManagerService threadPoolManagerService;

    @GetMapping("/api/anticipa-dashboard/thread-pool/list")
    public Result<List<ThreadPoolDetailRespDTO>> listThreadPool(@Valid ThreadPoolListReqDTO requestParam) {
        return Result.success(threadPoolManagerService.listThreadPool(requestParam));
    }

    @GetMapping("/api/thread-pools")
    public Result<PageDTO<ThreadPoolDetailRespDTO>> listThreadPoolPage(@Valid ThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        return Result.success(threadPoolManagerService.listThreadPoolPage(requestParam, pageReq));
    }

    @PostMapping("/api/anticipa-dashboard/thread-pool/update")
    public Result<Void> updateGlobalThreadPool(@RequestBody ThreadPoolUpdateReqDTO requestParam) {
        threadPoolManagerService.updateGlobalThreadPool(requestParam);
        return Result.success();
    }
}
