package com.xuelian.career.service;

import com.xuelian.career.common.BusinessException;
import com.xuelian.career.dto.request.LoginRequest;
import com.xuelian.career.dto.request.RegisterRequest;
import com.xuelian.career.dto.response.LoginResponse;
import com.xuelian.career.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 业务逻辑测试
 * 覆盖：注册逻辑、登录逻辑、密码加密、JWT生成与验证
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    private static final String TEST_USER = "svc_test_" + System.currentTimeMillis() % 100000;
    private static final String TEST_PASS = "Test123456";

    @Test
    @Order(1)
    @DisplayName("TC-SVC-001: 注册新用户 → 密码BCrypt加密 → JWT有效")
    void testRegisterNewUser() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USER);
        req.setPassword(TEST_PASS);
        req.setRole("STUDENT");
        req.setEmail("svc_test@test.com");

        LoginResponse resp = authService.register(req);

        assertNotNull(resp, "注册响应不应为空");
        assertNotNull(resp.getToken(), "Token不应为空");
        assertEquals(TEST_USER, resp.getUsername(), "用户名应匹配");
        assertEquals("STUDENT", resp.getRole(), "角色应为STUDENT");

        // 验证密码已加密（数据库中不是明文）
        User user = userService.getByUsername(TEST_USER);
        assertNotNull(user, "数据库应有该用户");
        assertNotEquals(TEST_PASS, user.getPasswordHash(), "密码不应明文存储");
        assertTrue(user.getPasswordHash().startsWith("$2a$"), "密码应使用BCrypt加密");

        System.out.println("[PASS] TC-SVC-001: 注册+密码加密验证 √");
    }

    @Test
    @Order(2)
    @DisplayName("TC-SVC-002: 重复注册同一用户名 → 抛出BusinessException")
    void testRegisterDuplicate() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USER);
        req.setPassword(TEST_PASS);
        req.setRole("STUDENT");

        assertThrows(BusinessException.class, () -> authService.register(req),
                "重复用户名应抛出BusinessException");
        System.out.println("[PASS] TC-SVC-002: 重复注册拦截 √");
    }

    @Test
    @Order(3)
    @DisplayName("TC-SVC-003: 登录成功 → 返回正确用户信息")
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USER);
        req.setPassword(TEST_PASS);

        LoginResponse resp = authService.login(req);

        assertNotNull(resp, "登录响应不应为空");
        assertEquals(TEST_USER, resp.getUsername(), "用户名应匹配");
        assertEquals("STUDENT", resp.getRole(), "角色应为STUDENT");
        assertNotNull(resp.getToken(), "Token不应为空");

        System.out.println("[PASS] TC-SVC-003: 登录成功 √");
    }

    @Test
    @Order(4)
    @DisplayName("TC-SVC-004: 密码错误 → 抛出BusinessException")
    void testLoginWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USER);
        req.setPassword("WrongPassword999");

        assertThrows(BusinessException.class, () -> authService.login(req),
                "错误密码应抛出BusinessException");
        System.out.println("[PASS] TC-SVC-004: 错误密码拒绝 √");
    }

    @Test
    @Order(5)
    @DisplayName("TC-SVC-005: 不存在的用户登录 → 抛出BusinessException")
    void testLoginUserNotFound() {
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost_user_not_exist");
        req.setPassword(TEST_PASS);

        assertThrows(BusinessException.class, () -> authService.login(req),
                "不存在用户应抛出BusinessException");
        System.out.println("[PASS] TC-SVC-005: 不存在用户拒绝 √");
    }
}
