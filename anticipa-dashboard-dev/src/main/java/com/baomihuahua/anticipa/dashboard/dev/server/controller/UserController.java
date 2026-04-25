package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserLoginReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.UserLoginRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    private static final List<UserLoginRespDTO> MOCK_USERS = new ArrayList<>();

    static {
        MOCK_USERS.add(UserLoginRespDTO.builder()
                .id("1")
                .username("admin")
                .password("admin")
                .realName("管理员")
                .accessToken("admin-token-abc123")
                .build());
        MOCK_USERS.add(UserLoginRespDTO.builder()
                .id("2")
                .username("test")
                .password("test")
                .realName("测试用户")
                .accessToken("test-token-def456")
                .build());
        MOCK_USERS.add(UserLoginRespDTO.builder()
                .id("3")
                .username("wjq")
                .password("wjq")
                .realName("王景全")
                .accessToken("wjq-token-ghi789")
                .build());
    }

    @PostMapping("/api/anticipa-dashboard/auth/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam) {
        for (UserLoginRespDTO user : MOCK_USERS) {
            if (user.getUsername().equals(requestParam.getUsername())
                    && user.getPassword().equals(requestParam.getPassword())) {
                return Result.success(user);
            }
        }
        return Result.failure(401, "用户名或密码错误");
    }

    @GetMapping("/api/anticipa-dashboard/user/detail")
    public Result<UserDetailRespDTO> detail() {
        UserDetailRespDTO userDetail = UserDetailRespDTO.builder()
                .id("1")
                .username("admin")
                .realName("管理员")
                .build();
        return Result.success(userDetail);
    }
}
