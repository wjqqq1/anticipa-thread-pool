package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserLoginReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserLoginRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final AnticipaProperties anticipaProperties;

    @PostMapping("/api/anticipa-dashboard/auth/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        String actualAuth = requestParam.getUsername() + "," + requestParam.getPassword();
        if (!anticipaProperties.getUsers().contains(actualAuth)) {
            return Result.failure(401, "用户名或密码错误");
        }

        // 调用 sa-token 进行登录，创建会话
        StpUtil.login(requestParam.getUsername());

        return Result.success(UserLoginRespDTO.builder()
                .id("1")
                .username(requestParam.getUsername())
                .password(requestParam.getPassword())
                .realName(requestParam.getUsername())
                .accessToken(StpUtil.getTokenValue())
                .build());
    }

    @GetMapping("/api/anticipa-dashboard/user/detail")
    public Result<UserDetailRespDTO> detail() {
        UserDetailRespDTO userDetail = UserDetailRespDTO.builder()
                .id("1")
                .username(StpUtil.getLoginIdAsString())
                .realName(StpUtil.getLoginIdAsString())
                .build();
        return Result.success(userDetail);
    }
}
