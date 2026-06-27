package com.xuelian.career.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController & EnterpriseController & CustomerServiceController 集成测试
 * 覆盖：管理功能、企业推荐、智能客服
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminAndBusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;

    /**
     * 前置：管理员登录获取 token
     */
    @BeforeAll
    static void setup(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) throws Exception {
        String loginBody = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readTree(resp).get("data").get("token").asText();
        System.out.println("[SETUP] Admin Token: " + adminToken.substring(0, 20) + "...");
    }

    // ==================== 管理端: 用户管理 ====================

    @Test
    @Order(1)
    @DisplayName("TC-ADMIN-001: 获取用户列表 → 返回分页数据")
    void testGetUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records").isArray());
        System.out.println("[PASS] TC-ADMIN-001: 用户列表分页 √");
    }

    @Test
    @Order(2)
    @DisplayName("TC-ADMIN-002: 无token访问 → 返回401")
    void testGetUsers_NoToken() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        System.out.println("[PASS] TC-ADMIN-002: 未认证拦截 √");
    }

    @Test
    @Order(3)
    @DisplayName("TC-ADMIN-003: 获取用户详情 → 返回用户信息")
    void testGetUserDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users/1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").isNotEmpty());
        System.out.println("[PASS] TC-ADMIN-003: 用户详情 √");
    }

    @Test
    @Order(4)
    @DisplayName("TC-ADMIN-004: 获取不存在用户 → 返回404")
    void testGetUserDetail_NotFound() throws Exception {
        mockMvc.perform(get("/api/admin/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        System.out.println("[PASS] TC-ADMIN-004: 404处理 √");
    }

    @Test
    @Order(5)
    @DisplayName("TC-ADMIN-005: 更新用户状态 → 返回200")
    void testUpdateUserStatus() throws Exception {
        String body = "{\"status\":\"DISABLED\"}";
        mockMvc.perform(put("/api/admin/users/2/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] TC-ADMIN-005: 状态更新 √");

        // 恢复状态
        String enableBody = "{\"status\":\"ACTIVE\"}";
        mockMvc.perform(put("/api/admin/users/2/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enableBody))
                .andExpect(status().isOk());
    }

    // ==================== 管理端: 技能词典 ====================

    @Test
    @Order(6)
    @DisplayName("TC-ADMIN-006: 获取技能列表 → 返回分页数据")
    void testGetSkills() throws Exception {
        mockMvc.perform(get("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(50)); // init.sql中50+技能
        System.out.println("[PASS] TC-ADMIN-006: 技能列表 √ (共50个技能)");
    }

    @Test
    @Order(7)
    @DisplayName("TC-ADMIN-007: 搜索技能关键词 → 返回匹配结果")
    void testSearchSkills() throws Exception {
        mockMvc.perform(get("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("Java"));
        System.out.println("[PASS] TC-ADMIN-007: 技能搜索 √");
    }

    @Test
    @Order(8)
    @DisplayName("TC-ADMIN-008: 新增技能 → 返回200")
    void testAddSkill() throws Exception {
        String body = "{\"name\":\"FastAPI_TestSkill\",\"category\":\"框架\",\"description\":\"Python现代Web框架(测试)\"}";
        mockMvc.perform(post("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] TC-ADMIN-008: 新增技能 √");
    }

    @Test
    @Order(9)
    @DisplayName("TC-ADMIN-009: 搜索并删除测试技能(逻辑删除) → 返回200")
    void testDeleteSkill() throws Exception {
        // 先搜索刚添加的技能获取实际ID，再删除
        String resp = mockMvc.perform(get("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("keyword", "FastAPI_TestSkill")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int skillId = objectMapper.readTree(resp).get("data").get("records").get(0).get("id").asInt();
        mockMvc.perform(delete("/api/admin/skills/" + skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        System.out.println("[PASS] TC-ADMIN-009: 搜索到技能ID=" + skillId + " → 逻辑删除 √");
    }

    // ==================== 管理端: 数据看板 ====================

    @Test
    @Order(10)
    @DisplayName("TC-ADMIN-010: 获取运营看板 → 返回统计数据")
    void testGetDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalUsers").isNumber());
        System.out.println("[PASS] TC-ADMIN-010: 运营看板 √");
    }

    // ==================== 智能客服 ====================

    @Test
    @Order(11)
    @DisplayName("TC-CS-001: 智能客服聊天 → 返回AI回答")
    void testCustomerServiceChat() throws Exception {
        String body = "{\"question\":\"Java需要学多久？\",\"userRole\":\"STUDENT\"}";
        mockMvc.perform(post("/api/customer-service/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answer").isNotEmpty())
                .andExpect(jsonPath("$.data.source").isNotEmpty());
        System.out.println("[PASS] TC-CS-001: 智能客服 √");
    }

    @Test
    @Order(12)
    @DisplayName("TC-CS-002: 智能客服空问题 → 返回400或兜底回答")
    void testCustomerServiceEmptyQuestion() throws Exception {
        String body = "{\"question\":\"\",\"userRole\":\"STUDENT\"}";
        mockMvc.perform(post("/api/customer-service/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()); // 应有兜底回答
        System.out.println("[PASS] TC-CS-002: 空问题兜底 √");
    }

    @Test
    @Order(13)
    @DisplayName("TC-CS-003: 获取FAQ列表 → 返回40+条")
    void testGetFAQs() throws Exception {
        mockMvc.perform(get("/api/customer-service/faqs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(41)); // init.sql中41条FAQ
        System.out.println("[PASS] TC-CS-003: FAQ列表 √ (共41条)");
    }

    // ==================== 企业端 ====================

    @Test
    @Order(14)
    @DisplayName("TC-ENT-001: 企业项目推荐 → 返回岗位和候选人")
    void testEnterpriseRecommend() throws Exception {
        String body = "{\"projectDescription\":\"开发一个支持高并发的在线商城，包含用户端小程序和商家管理后台\"}";
        mockMvc.perform(post("/api/enterprise/recommend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] TC-ENT-001: 企业推荐 √");
    }

    @Test
    @Order(15)
    @DisplayName("TC-ENT-002: 企业推荐-描述过短 → 返回错误")
    void testEnterpriseRecommend_ShortDescription() throws Exception {
        String body = "{\"projectDescription\":\"商城\"}";  // 少于20字符
        mockMvc.perform(post("/api/enterprise/recommend")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
        System.out.println("[PASS] TC-ENT-002: 短描述拦截 √");
    }

    @Test
    @Order(16)
    @DisplayName("TC-ENT-003: 获取推荐历史 → 返回列表")
    void testGetRecommendHistory() throws Exception {
        mockMvc.perform(get("/api/enterprise/recommend/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        System.out.println("[PASS] TC-ENT-003: 推荐历史 √");
    }
}
