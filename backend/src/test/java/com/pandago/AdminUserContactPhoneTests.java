package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台用户管理「单一联系电话」验收。
 *
 * <p>产品决策：用户不再看到两列重复的手机号，平台只展示一个“联系电话”，
 * 数据来源固定为 user.phone。商家资料页仍然维护 merchant_profile.contact_phone，
 * 但由后端与 user.phone 联动同步，管理端 VO 不再暴露该内部字段。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserContactPhoneTests {

    private static final long ADMIN = 9700401L;
    private static final long MERCHANT = 9700402L;
    private static final long NORMAL_USER = 9700403L;
    private static final String SHOP_PHONE = "18739029473";
    private static final String USER_PHONE = "13800001111";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertUser(ADMIN, "au_admin", null);
        insertUser(MERCHANT, "au_merchant", USER_PHONE);
        insertUser(NORMAL_USER, "au_user", null);
        role(ADMIN, "ADMIN");
        role(MERCHANT, "MERCHANT");
        role(NORMAL_USER, "USER");
        jdbcTemplate.update("INSERT INTO merchant_profile(user_id, store_name, contact_phone, status, audit_status) "
                + "VALUES (?,?,?,'NORMAL','APPROVED')", MERCHANT, "测试店铺", SHOP_PHONE);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM merchant_profile WHERE user_id IN (?,?,?)", ADMIN, MERCHANT, NORMAL_USER);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?,?)", ADMIN, MERCHANT, NORMAL_USER);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?,?)", ADMIN, MERCHANT, NORMAL_USER);
    }

    private void insertUser(long id, String username, String phone) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, phone, status, deleted) "
                        + "VALUES (?,?,?,?,?,1,0)",
                id, username, "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu",
                username, phone);
    }

    private void role(long userId, String code) {
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code=?", userId, code);
    }

    private String adminToken() {
        return "Bearer " + jwtTokenProvider.generateToken(ADMIN, "au_admin", List.of(RoleCode.ADMIN));
    }

    private JsonNode findUser(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.path("id").asLong() == id) return node;
        }
        return null;
    }

    @Test
    void adminUserList_returnsSinglePhoneAndHidesInternalStorePhone() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", adminToken())
                        .param("pageNum", "1").param("pageSize", "50"))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsByteArray()).path("data");
        JsonNode list = data.path("list");

        JsonNode merchant = findUser(list, MERCHANT);
        JsonNode normal = findUser(list, NORMAL_USER);
        assertTrue(merchant != null && normal != null, "分页应包含测试用户");

        // 管理端只暴露一个联系电话，来源固定为 user.phone
        assertEquals(USER_PHONE, merchant.path("phone").asText());
        assertEquals(SHOP_PHONE, jdbcTemplate.queryForObject(
                "SELECT contact_phone FROM merchant_profile WHERE user_id=?", String.class, MERCHANT));
        assertTrue(merchant.path("contactPhone").isMissingNode(),
                "管理端 VO 不应再暴露内部字段 contactPhone");

        // 没有手机号的普通用户显示为空
        assertTrue(normal.path("phone").isMissingNode() || normal.path("phone").isNull());
    }

    @Test
    void adminUserList_nonAdmin_forbidden() throws Exception {
        String userToken = "Bearer " + jwtTokenProvider.generateToken(NORMAL_USER, "au_user", List.of(RoleCode.USER));
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }
}
