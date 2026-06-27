package com.xuelian.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelian.career.dto.request.LoginRequest;
import com.xuelian.career.dto.request.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 认证接口测试
 * 覆盖：注册成功/失败场景、登录成功/失败场景、参数校验、边界值
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "test_stu_" + System.currentTimeMillis() % 100000;
    private static final String TEST_PASSWORD = "abc123";
    private static String jwtToken;

    // ========== 主流程 ==========

    @Test
    @Order(1)
    @DisplayName("TC-AUTH-001: 学生注册成功 → 返回200+token")
    void testRegisterStudentSuccess() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");
        req.setPhone("13800138000");
        req.setEmail("test@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));
        System.out.println("[PASS] TC-AUTH-001: 学生注册成功 √");
    }

    @Test
    @Order(2)
    @DisplayName("TC-AUTH-002: 登录成功 → 返回200+token+角色")
    void testLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andReturn().getResponse().getContentAsString();

        jwtToken = objectMapper.readTree(response).get("data").get("token").asText();
        System.out.println("[PASS] TC-AUTH-002: 登录成功 √, token=" + jwtToken.substring(0, 20) + "...");
    }

    // ========== 参数校验异常 ==========

    @Test
    @Order(3)
    @DisplayName("TC-AUTH-003: 注册-用户名为空 → 返回400")
    void testRegisterEmptyUsername() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-003: 空用户名拦截 √");
    }

    @Test
    @Order(4)
    @DisplayName("TC-AUTH-004: 注册-密码过短(<6位) → 返回400")
    void testRegisterShortPassword() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("shortpwd_user");
        req.setPassword("12345");
        req.setRole("STUDENT");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-004: 短密码拦截 √");
    }

    @Test
    @Order(5)
    @DisplayName("TC-AUTH-005: 注册-非法角色 → 返回400")
    void testRegisterInvalidRole() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("role_test_u");
        req.setPassword(TEST_PASSWORD);
        req.setRole("SUPER_ADMIN");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-005: 非法角色拦截 √");
    }

    // ========== 边界值 ==========

    @Test
    @Order(6)
    @DisplayName("TC-AUTH-006: 注册-用户名边界(3位) → 返回200")
    void testRegisterUsernameMinLength() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("abc");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        System.out.println("[PASS] TC-AUTH-006: 3位用户名边界 √");
    }

    @Test
    @Order(7)
    @DisplayName("TC-AUTH-007: 注册-用户名边界(2位) → 返回400")
    void testRegisterUsernameTooShort() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("ab");
        req.setPassword(TEST_PASSWORD);
        req.setRole("STUDENT");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-007: 2位用户名拦截 √");
    }

    // ========== 登录异常 ==========

    @Test
    @Order(8)
    @DisplayName("TC-AUTH-008: 登录-密码错误 → 返回401")
    void testLoginWrongPassword() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrong_password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-008: 错误密码拒绝 √");
    }

    @Test
    @Order(9)
    @DisplayName("TC-AUTH-009: 登录-用户名不存在 → 返回401")
    void testLoginUserNotFound() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent_user_9999");
        req.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-AUTH-009: 不存在用户拒绝 √");
    }

    // ========== 响应格式验证 ==========

    @Test
    @Order(10)
    @DisplayName("TC-AUTH-010: 验证登录响应结构完整性")
    void testLoginResponseStructure() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.username").isString())
                .andExpect(jsonPath("$.data.role").isString())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.expireTime").isNumber());
        System.out.println("[PASS] TC-AUTH-010: 响应结构验证 √");
    }
}
