package com.xuelian.career.controller;

import com.xuelian.career.common.Result;
import com.xuelian.career.dto.request.LoginRequest;
import com.xuelian.career.dto.request.RegisterRequest;
import com.xuelian.career.dto.response.LoginResponse;
import com.xuelian.career.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 处理用户注册与登录请求
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("收到注册请求: username={}, role={}", request.getUsername(), request.getRole());
        LoginResponse response = authService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("收到登录请求: username={}", request.getUsername());
        LoginResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }
}
