package com.xuelian.career.service.impl;

import com.xuelian.career.common.BusinessException;
import com.xuelian.career.dto.request.LoginRequest;
import com.xuelian.career.dto.request.RegisterRequest;
import com.xuelian.career.dto.response.LoginResponse;
import com.xuelian.career.entity.User;
import com.xuelian.career.service.AuthService;
import com.xuelian.career.service.UserService;
import com.xuelian.career.util.JwtUtil;
import com.xuelian.career.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类 - 实现注册与登录的完整业务流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordUtil passwordUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis Key 前缀：登录会话 */
    private static final String TOKEN_PREFIX = "auth:token:";

    /**
     * 用户注册流程：
     * 1. 校验用户名唯一性
     * 2. BCrypt 加密密码
     * 3. 保存用户到数据库
     * 4. 生成 JWT Token
     * 5. Redis 存储会话
     * 6. 返回登录信息
     */
    @Override
    public LoginResponse register(RegisterRequest request) {
        // 1. 校验用户名是否已存在
        if (userService.isUsernameExists(request.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 2. 创建用户实体
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordUtil.encode(request.getPassword()));
        user.setRole(request.getRole());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 3. 保存用户
        userService.createUser(user);
        log.info("新用户注册成功: username={}, role={}", user.getUsername(), user.getRole());

        // 4. 生成 Token 并返回
        return buildLoginResponse(user);
    }

    /**
     * 用户登录流程：
     * 1. 查询用户
     * 2. BCrypt 校验密码
     * 3. 检查账号状态
     * 4. 生成 JWT Token
     * 5. Redis 存储会话
     * 6. 返回登录信息
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 查询用户
        User user = userService.getByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 2. 校验密码
        if (!passwordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 检查账号状态
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        log.info("用户登录成功: userId={}, role={}", user.getId(), user.getRole());

        // 4. 生成 Token 并返回
        return buildLoginResponse(user);
    }

    /**
     * 构建登录响应：生成 JWT Token，存储 Redis 会话
     * @param user 用户实体
     * @return 登录响应
     */
    private LoginResponse buildLoginResponse(User user) {
        // 生成 JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // Token 过期时间（秒）
        long expireSeconds = jwtUtil.getExpiration();

        // Redis 存储会话：key=auth:token:{token} → value=userId，设置过期时间
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + token,
                user.getId(),
                expireSeconds,
                TimeUnit.SECONDS
        );

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .expireTime(System.currentTimeMillis() / 1000 + expireSeconds)
                .build();
    }
}
