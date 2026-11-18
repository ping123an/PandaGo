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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 商家手机号双向联动专项测试。
 *
 * <p>覆盖：个人中心改手机号同步店铺、商家资料改手机号同步个人、清空同步、
 * 固话被拒绝、普通用户不写店铺表、角色以数据库为准。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantPhoneLinkTests {

    private static final long MERCHANT_A = 9700401L;
    private static final long MERCHANT_B = 9700402L;
    private static final long PLAIN_USER = 9700403L;
    private static final String HASH = "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
        insertUser(MERCHANT_A, "link_merchant_a");
        insertUser(MERCHANT_B, "link_merchant_b");
        insertUser(PLAIN_USER, "link_plain_user");
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", MERCHANT_A);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='MERCHANT'", MERCHANT_A);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", MERCHANT_B);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='MERCHANT'", MERCHANT_B);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", PLAIN_USER);
        insertProfile(MERCHANT_A, "13900000001");
        insertProfile(MERCHANT_B, "13900000002");
    }

    @AfterEach
    void cleanUp() {
        clean();
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM merchant_profile WHERE user_id IN (?,?)", MERCHANT_A, MERCHANT_B);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?,?)", MERCHANT_A, MERCHANT_B, PLAIN_USER);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?,?)", MERCHANT_A, MERCHANT_B, PLAIN_USER);
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, status, deleted) "
                        + "VALUES (?,?,?,?,1,0)", id, username, HASH, "联动测试用户");
    }

    private void insertProfile(long userId, String phone) {
        jdbcTemplate.update("INSERT INTO merchant_profile(user_id, store_name, contact_phone, status, audit_status) "
                        + "VALUES (?,?,?,'NORMAL','APPROVED')", userId, "联动测试店铺", phone);
    }

    private String token(long userId, RoleCode... roles) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(roles));
    }

    private String phoneOfUser(long userId) {
        return jdbcTemplate.queryForObject("SELECT phone FROM user WHERE id=?", String.class, userId);
    }

    private String phoneOfStore(long userId) {
        return jdbcTemplate.queryForObject("SELECT contact_phone FROM merchant_profile WHERE user_id=?",
                String.class, userId);
    }

    private MvcResult updateUserProfile(long userId, String phone) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("nickname", "联动测试用户", "phone", phone == null ? "" : phone));
        return mockMvc.perform(put("/api/v1/users/me/profile")
                        .header("Authorization", token(userId, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private MvcResult updateStoreProfile(long userId, String contactPhone) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("storeName", "联动测试店铺");
        payload.put("contactPhone", contactPhone);
        String body = objectMapper.writeValueAsString(payload);
        return mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(userId, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String message(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void userPhoneChange_syncsToStoreContactPhone() throws Exception {
        MvcResult result = updateUserProfile(MERCHANT_A, "13811112222");
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertEquals("13811112222", phoneOfUser(MERCHANT_A));
        assertEquals("13811112222", phoneOfStore(MERCHANT_A), "个人中心手机号应同步到店铺联系电话");
        // 返回 VO 仍然只反映当前用户资料
        assertEquals("13811112222", json(result).path("data").path("phone").asText());
    }

    @Test
    void userPhoneCleared_syncsStoreEmpty() throws Exception {
        MvcResult result = updateUserProfile(MERCHANT_A, null);
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertNull(phoneOfUser(MERCHANT_A));
        assertNull(phoneOfStore(MERCHANT_A), "清空个人手机号应同步清空店铺联系电话");
    }

    @Test
    void storeMobileChange_syncsToUserPhone() throws Exception {
        MvcResult result = updateStoreProfile(MERCHANT_A, "13733334444");
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertEquals("13733334444", phoneOfStore(MERCHANT_A));
        assertEquals("13733334444", phoneOfUser(MERCHANT_A), "店铺手机号应同步到个人资料");
        assertEquals("13733334444", json(result).path("data").path("contactPhone").asText());
    }

    @Test
    void storePhoneCleared_syncsUserEmpty() throws Exception {
        MvcResult result = updateStoreProfile(MERCHANT_A, "");
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertNull(phoneOfStore(MERCHANT_A));
        assertNull(phoneOfUser(MERCHANT_A), "清空店铺联系电话应同步清空个人手机号");
    }

    @Test
    void storeLandline_isRejected_afterPhoneMerge() throws Exception {
        // 合并为一个联系电话后，店铺不再支持固话，避免平台只显示一列时两边又不一致
        updateUserProfile(MERCHANT_A, "13811112222");
        MvcResult result = updateStoreProfile(MERCHANT_A, "020-88886666");
        assertEquals(400, result.getResponse().getStatus(), message(result));
        assertTrue(message(result).contains("联系电话格式不正确"));
        assertEquals("13811112222", phoneOfStore(MERCHANT_A));
        assertEquals("13811112222", phoneOfUser(MERCHANT_A));
    }

    @Test
    void plainUserPhoneChange_doesNotTouchMerchantProfile() throws Exception {
        MvcResult result = updateUserProfile(PLAIN_USER, "13655556666");
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertEquals("13655556666", phoneOfUser(PLAIN_USER));
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_profile WHERE user_id=?", Integer.class, PLAIN_USER);
        assertEquals(0, rows, "普通用户不应被创建/修改店铺资料");
    }

    @Test
    void roleIsReadFromDatabase_notFromToken() throws Exception {
        // 故意用 USER token 调个人资料接口：数据库里该账号仍是商家，必须同步
        MvcResult result = updateUserProfile(MERCHANT_B, "13577778888");
        assertEquals(200, result.getResponse().getStatus(), message(result));
        assertEquals("13577778888", phoneOfStore(MERCHANT_B));
    }

    @Test
    void invalidPhone_isRejectedBeforeAnyWrite() throws Exception {
        MvcResult result = updateStoreProfile(MERCHANT_A, "12345");
        assertEquals(400, result.getResponse().getStatus(), message(result));
        assertTrue(message(result).contains("联系电话格式不正确"));
        assertEquals("13900000001", phoneOfStore(MERCHANT_A));
        assertNull(phoneOfUser(MERCHANT_A));
    }
}
