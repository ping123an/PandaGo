package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.JwtTokenProvider;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人资料更新验收测试。
 *
 * <p>覆盖：修改昵称/手机号、清空手机号、空白昵称/非法手机号被拒、未登录 401、
 * 不能改他人资料、不能改 userId/username/password/角色、响应不含密码字段、
 * 与既有 /auth/me 数据一致。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileTests {

    private static final long USER_A = 9700201L;
    private static final long USER_B = 9700202L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertUser(USER_A, "prof_a", "原始昵称A", "13800000001");
        insertUser(USER_B, "prof_b", "原始昵称B", "13900000002");
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", USER_A);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", USER_B);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?)", USER_A, USER_B);
    }

    private void insertUser(long id, String username, String nickname, String phone) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, phone, status, deleted) "
                + "VALUES (?,?,?,?,?,1,0)", id, username, "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu", nickname, phone);
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(role));
    }

    private JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsByteArray());
    }

    private String body(String nickname, String phone) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("nickname", nickname, "phone", phone));
    }

    @Test
    void updateNicknameAndPhone_success() throws Exception {
        MvcResult r = mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("熊猫买家", "13712345678")))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json(r).path("data");
        assertEquals("熊猫买家", data.path("nickname").asText());
        assertEquals("13712345678", data.path("phone").asText());
        assertEquals(USER_A, data.path("userId").asLong());

        // 回读数据库确认落库
        User fresh = userMapper.selectById(USER_A);
        assertEquals("熊猫买家", fresh.getNickname());
        assertEquals("13712345678", fresh.getPhone());
    }

    @Test
    void updateProfile_emptyPhone_clearsPhone() throws Exception {
        MvcResult r = mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("新昵称", "")))
                .andExpect(status().isOk()).andReturn();
        // Jackson 配置省略 null 字段：phone 可能不存在或显式为 null
        JsonNode phoneNode = json(r).path("data").path("phone");
        assertTrue(phoneNode.isMissingNode() || phoneNode.isNull());
        assertNull(userMapper.selectById(USER_A).getPhone());
    }

    @Test
    void updateProfile_blankNickname_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("   ", "13800000001")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_invalidPhone_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("合法昵称", "12345")))
                .andExpect(status().isBadRequest());
        // 数据库不应被改动
        assertEquals("原始昵称A", userMapper.selectById(USER_A).getNickname());
    }

    @Test
    void updateProfile_noToken_unauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON).content(body("任意昵称", "13800000001")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_ignoresSensitiveFields() throws Exception {
        // 夹带 userId / username / password / passwordHash / status / roles：白名单必须忽略
        String evil = "{\"nickname\":\"合法昵称\",\"phone\":\"13700000000\","
                + "\"userId\":" + USER_B + ",\"username\":\"hacked\","
                + "\"password\":\"hacked123\",\"passwordHash\":\"hacked\","
                + "\"status\":0,\"roles\":[\"ADMIN\"]}";
        MvcResult r = mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(evil))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json(r).path("data");
        assertEquals(USER_A, data.path("userId").asLong(), "userId 不接受前端篡改");
        assertEquals("prof_a", data.path("username").asText(), "用户名不接受前端篡改");
        assertTrue(data.path("roles").toString().contains("USER"), "角色不接受前端篡改");
        assertFalse(data.path("roles").toString().contains("ADMIN"));

        User fresh = userMapper.selectById(USER_A);
        assertEquals("prof_a", fresh.getUsername());
        assertEquals(1, fresh.getStatus(), "账号状态不接受前端篡改");
        assertTrue(fresh.getPasswordHash().startsWith("$2a$"), "密码哈希未被改动");

        // 不能改到其他用户
        assertEquals("原始昵称B", userMapper.selectById(USER_B).getNickname());
    }

    @Test
    void updateProfile_noPasswordFieldInResponse() throws Exception {
        MvcResult r = mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("安全昵称", "13800000001")))
                .andExpect(status().isOk()).andReturn();
        String text = r.getResponse().getContentAsString();
        assertFalse(text.contains("password"), "响应不应出现 password 字段");
        assertFalse(text.contains("passwordHash"), "响应不应出现 passwordHash 字段");
    }

    @Test
    void getProfile_matchesUpdatedData_andNoPassword() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("一致昵称", "13600000000")))
                .andExpect(status().isOk());

        MvcResult me = mockMvc.perform(get("/api/v1/users/me/profile")
                        .header("Authorization", token(USER_A, RoleCode.USER)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = json(me).path("data");
        assertEquals("一致昵称", data.path("nickname").asText());
        assertEquals("13600000000", data.path("phone").asText());
        assertNotEquals("原始昵称A", data.path("nickname").asText());
        assertFalse(me.getResponse().getContentAsString().contains("password"));
    }
}
