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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** 商家店铺头像上传专项测试：落盘、写库、静态访问、权限与非法文件拦截。 */
@SpringBootTest(properties = "pandago.upload.base-dir=build/test-merchant-logo-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantLogoUploadTests {

    private static final long MERCHANT_A = 9700501L;
    private static final long MERCHANT_B = 9700502L;
    private static final long PLAIN_USER = 9700503L;
    private static final Path AVATAR_DIR = Paths.get("build/test-merchant-logo-uploads/avatars").toAbsolutePath().normalize();
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final String HASH = "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final Set<Path> createdFiles = new HashSet<>();

    @BeforeEach
    void setUp() {
        createdFiles.clear();
        clean();
        insertUser(MERCHANT_A, "logo_merchant_a");
        insertUser(MERCHANT_B, "logo_merchant_b");
        insertUser(PLAIN_USER, "logo_plain_user");
        grant(MERCHANT_A, "MERCHANT");
        grant(MERCHANT_B, "MERCHANT");
        grant(PLAIN_USER, "USER");
        insertProfile(MERCHANT_A);
        insertProfile(MERCHANT_B);
    }

    @AfterEach
    void cleanUp() {
        clean();
        for (Path file : createdFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // build 目录由 maven clean 统一清理
            }
        }
        createdFiles.clear();
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM merchant_profile WHERE user_id IN (?,?)", MERCHANT_A, MERCHANT_B);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?,?)", MERCHANT_A, MERCHANT_B, PLAIN_USER);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?,?)", MERCHANT_A, MERCHANT_B, PLAIN_USER);
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, status, deleted) "
                + "VALUES (?,?,?,?,1,0)", id, username, HASH, "头像测试商家");
    }

    private void grant(long userId, String role) {
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code=?", userId, role);
    }

    private void insertProfile(long userId) {
        jdbcTemplate.update("INSERT INTO merchant_profile(user_id, store_name, status, audit_status) "
                + "VALUES (?,?,'NORMAL','APPROVED')", userId, "头像测试店铺");
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(role));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String message(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private MvcResult upload(long userId, RoleCode role, String filename, String contentType, byte[] bytes,
                             String extraMerchantId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, bytes);
        var request = multipart("/api/v1/merchant/profile/logo").file(file);
        if (role != null) {
            request.header("Authorization", token(userId, role));
        }
        if (extraMerchantId != null) {
            request.param("merchantId", extraMerchantId).param("userId", extraMerchantId);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String uploadOk(String filename, String contentType, byte[] bytes, String expectExtension) throws Exception {
        MvcResult result = upload(MERCHANT_A, RoleCode.MERCHANT, filename, contentType, bytes, null);
        assertEquals(200, result.getResponse().getStatus(), message(result));
        String url = json(result).path("data").path("logoUrl").asText();
        assertTrue(url.startsWith("/uploads/avatars/"), "应返回相对 URL：" + url);
        assertTrue(url.endsWith("." + expectExtension));

        Path saved = AVATAR_DIR.resolve(url.substring(url.lastIndexOf('/') + 1)).normalize();
        createdFiles.add(saved);
        assertTrue(Files.exists(saved));
        assertArrayEquals(bytes, Files.readAllBytes(saved));
        assertEquals(url, jdbcTemplate.queryForObject(
                "SELECT logo FROM merchant_profile WHERE user_id=?", String.class, MERCHANT_A));
        return url;
    }

    @Test
    void upload_png_success() throws Exception {
        String url = uploadOk("logo.PNG", "image/png", PNG_MAGIC, "png");
        assertTrue(url.matches("/uploads/avatars/[0-9a-f]{32}\\.png"), "文件名应为 UUID 随机名：" + url);
    }

    @Test
    void upload_jpeg_success() throws Exception {
        uploadOk("logo.jpg", "image/jpg", JPEG_MAGIC, "jpeg");
    }

    @Test
    void upload_twice_doesNotOverwritePreviousFile() throws Exception {
        String first = uploadOk("same.png", "image/png", PNG_MAGIC, "png");
        String second = uploadOk("same.png", "image/png", PNG_MAGIC, "png");
        assertFalse(first.equals(second));
        assertTrue(Files.exists(AVATAR_DIR.resolve(first.substring(first.lastIndexOf('/') + 1))));
        assertTrue(Files.exists(AVATAR_DIR.resolve(second.substring(second.lastIndexOf('/') + 1))));
    }

    @Test
    void upload_getProfileReturnsLatestLogo() throws Exception {
        String url = uploadOk("view.png", "image/png", PNG_MAGIC, "png");
        MvcResult profile = mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andReturn();
        assertEquals(200, profile.getResponse().getStatus());
        assertEquals(url, json(profile).path("data").path("logo").asText());
    }

    @Test
    void uploadedFile_isPubliclyReadable() throws Exception {
        String url = uploadOk("public.png", "image/png", PNG_MAGIC, "png");
        MvcResult view = mockMvc.perform(get(url)).andReturn();
        assertEquals(200, view.getResponse().getStatus());
        assertTrue(view.getResponse().getContentType().contains(MediaType.IMAGE_PNG_VALUE));
        assertArrayEquals(PNG_MAGIC, Arrays.copyOf(view.getResponse().getContentAsByteArray(), PNG_MAGIC.length));
    }

    @Test
    void upload_ignoresMerchantIdParam_cannotModifyOtherStore() throws Exception {
        MvcResult result = upload(MERCHANT_A, RoleCode.MERCHANT, "mine.png", "image/png", PNG_MAGIC,
                String.valueOf(MERCHANT_B));
        assertEquals(200, result.getResponse().getStatus(), message(result));
        String url = json(result).path("data").path("logoUrl").asText();
        createdFiles.add(AVATAR_DIR.resolve(url.substring(url.lastIndexOf('/') + 1)));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT logo FROM merchant_profile WHERE user_id=?", String.class, MERCHANT_B));
    }

    @Test
    void upload_nonMerchant_forbidden() throws Exception {
        MvcResult result = upload(PLAIN_USER, RoleCode.USER, "a.png", "image/png", PNG_MAGIC, null);
        assertEquals(403, result.getResponse().getStatus());
    }

    @Test
    void upload_noToken_unauthorized() throws Exception {
        MvcResult result = upload(MERCHANT_A, null, "a.png", "image/png", PNG_MAGIC, null);
        assertEquals(401, result.getResponse().getStatus());
    }

    @Test
    void upload_exeDisguisedAsPng_rejected() throws Exception {
        byte[] fake = "MZ\u0090\u0000\u0003".getBytes(StandardCharsets.ISO_8859_1);
        MvcResult result = upload(MERCHANT_A, RoleCode.MERCHANT, "virus.png", "image/png", fake, null);
        assertEquals(400, result.getResponse().getStatus());
        assertNull(jdbcTemplate.queryForObject(
                "SELECT logo FROM merchant_profile WHERE user_id=?", String.class, MERCHANT_A));
    }

    @Test
    void upload_over2MB_rejected() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) 0x41);
        MvcResult result = upload(MERCHANT_A, RoleCode.MERCHANT, "big.png", "image/png", big, null);
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(message(result).contains("2MB"));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT logo FROM merchant_profile WHERE user_id=?", String.class, MERCHANT_A));
    }
}
