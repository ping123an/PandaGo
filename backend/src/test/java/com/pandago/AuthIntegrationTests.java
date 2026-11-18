package com.pandago;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.JwtTokenProvider;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 3 认证与权限集成测试，覆盖用户要求的 10 个验收场景。
 *
 * <p>@Transactional 保证每个测试结束后回滚，注册产生的用户不会污染数据库。
 * 使用随机用户名避免唯一键冲突（即使回滚，同一批次内也可能并发执行）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTests {

    private static final String RAW_PASSWORD = "Test@12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    private String uniqueUsername() {
        return "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String registerBody(String username) {
        return String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}",
                username, RAW_PASSWORD, RAW_PASSWORD);
    }

    /** 场景 1：正常注册 —— DB 中密码为 BCrypt 哈希，且分配了 USER 角色 */
    @Test
    void registerSuccessWithBcryptAndUserRole() throws Exception {
        String username = uniqueUsername();
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(body).path("data").asLong();
        assertTrue(userId > 0);

        User user = userMapper.selectById(userId);
        assertTrue(user != null);
        // 密码必须是 BCrypt 哈希，且能校验通过，绝不等于明文
        assertFalse(RAW_PASSWORD.equals(user.getPasswordHash()));
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, user.getPasswordHash()));

        // 角色表中应有该用户的 USER 记录
        List<?> roles = roleMapperRoles(userId);
        assertTrue(roles != null && !roles.isEmpty());
    }

    // 通过 mapper 拿角色（注入避免多写 SQL）
    @Autowired
    private com.pandago.role.mapper.RoleMapper roleMapper;

    private List<com.pandago.role.entity.Role> roleMapperRoles(Long userId) {
        return roleMapper.selectRolesByUserId(userId);
    }

    /** 场景 2：重复用户名注册 —— 409，不产生第二个用户 */
    @Test
    void duplicateUsernameReturns409() throws Exception {
        String username = uniqueUsername();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        assertTrue(count == 1, "重复注册不应产生第二个用户");
    }

    /** 场景 3：错误密码登录 —— 401 */
    @Test
    void wrongPasswordReturns401() throws Exception {
        String username = uniqueUsername();
        register(username);
        String wrong = String.format("{\"username\":\"%s\",\"password\":\"Wrong@999\"}", username);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 场景 4：正确密码登录 —— 返回 Token / 过期时间 / 用户信息，且无密码字段 */
    @Test
    void loginSuccessAndNoPasswordLeak() throws Exception {
        String username = uniqueUsername();
        register(username);
        String loginBody = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, RAW_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.expiresIn").value(86400000))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(jwtTokenProvider.validate(data.path("token").asText()));
    }

    /** 场景 5：无 Token 访问受保护接口 —— 401 且统一 Result */
    @Test
    void noTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }

    /** 场景 6：无效 Token 访问接口 —— 401 */
    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer this.is.invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 场景 7：过期 Token 访问接口 —— 401 */
    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = jwtTokenProvider.generateToken(1L, "expired", List.of(RoleCode.USER), -60000L);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 场景 8：USER 访问 ADMIN 接口 —— 403 */
    @Test
    void userCannotAccessAdminEndpoint() throws Exception {
        String username = uniqueUsername();
        register(username);
        String token = login(username);
        mockMvc.perform(get("/api/v1/demo/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 场景 9：禁用用户登录 —— 403 */
    @Test
    void disabledUserCannotLogin() throws Exception {
        String username = uniqueUsername();
        register(username);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        user.setStatus(0);
        userMapper.updateById(user);

        String loginBody = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, RAW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 场景 10：响应中不含任何密码字段（注册 / 登录 / me 三处） */
    @Test
    void noPasswordFieldInAnyAuthResponse() throws Exception {
        String username = uniqueUsername();
        String regResp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody(username)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(regResp.toLowerCase().contains("password"));

        String token = login(username);
        String meResp = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(meResp.toLowerCase().contains("password"));
        assertFalse(meResp.toLowerCase().contains("passwordHash".toLowerCase()));
    }

    /** 附加：注册不能通过请求参数把自己设为 ADMIN（角色字段被忽略） */
    @Test
    void registerCannotEscalateRole() throws Exception {
        String username = uniqueUsername();
        String body = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\",\"roleCode\":\"ADMIN\"}",
                username, RAW_PASSWORD, RAW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        String token = login(username);
        // 用该 token 访问 ADMIN 接口仍应被拒绝
        mockMvc.perform(get("/api/v1/demo/admin").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /** 身份登录：普通 USER 以 USER 身份登录应成功（前端身份选择入口兼容） */
    @Test
    void loginAsUserSucceedsForNormalUser() throws Exception {
        String username = uniqueUsername();
        register(username);
        String loginBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"loginAs\":\"USER\"}",
                username, RAW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    /** 身份登录：普通 USER 冒用商家身份登录应被拒绝（403），不能靠选择身份进入后台 */
    @Test
    void normalUserCannotLoginAsMerchant() throws Exception {
        String username = uniqueUsername();
        register(username);
        String loginBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"loginAs\":\"MERCHANT\"}",
                username, RAW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 身份登录：普通 USER 冒用管理员身份登录应被拒绝（403） */
    @Test
    void normalUserCannotLoginAsAdmin() throws Exception {
        String username = uniqueUsername();
        register(username);
        String loginBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\",\"loginAs\":\"ADMIN\"}",
                username, RAW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 附加：登录后可访问 /me，返回正确用户 id */
    @Test
    void meReturnsCurrentUser() throws Exception {
        String username = uniqueUsername();
        register(username);
        String token = login(username);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(registerBody(username)));
    }

    private String login(String username) throws Exception {
        String loginBody = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, RAW_PASSWORD);
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("token").asText();
    }
}
