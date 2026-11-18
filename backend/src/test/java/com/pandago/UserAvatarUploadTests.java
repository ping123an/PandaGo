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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 头像上传专项验收测试。
 *
 * <p>覆盖：JPG/PNG/WEBP 上传成功、超大文件、不支持格式、伪装文件、空文件、
 * 未登录拒绝、不能改他人头像、随机文件名、数据库只存相对 URL、静态资源可访问。</p>
 *
 * <p>上传目录通过 properties 指向 build/test-avatar-uploads，避免污染运行中的 uploads 目录。</p>
 */
@SpringBootTest(properties = "pandago.upload.base-dir=build/test-avatar-uploads")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAvatarUploadTests {

    private static final long USER_A = 9700301L;
    private static final long USER_B = 9700302L;
    private static final Path AVATAR_DIR = Paths.get("build/test-avatar-uploads/avatars").toAbsolutePath().normalize();

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] WEBP_MAGIC = new byte[]{
            'R', 'I', 'F', 'F', 0x10, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 记录本次测试产生的文件，@AfterEach 精确清理，不用通配符删目录 */
    private final Set<Path> createdFiles = new HashSet<>();

    @BeforeEach
    void setUp() {
        createdFiles.clear();
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?)", USER_A, USER_B);
        insertUser(USER_A, "avatar_a");
        insertUser(USER_B, "avatar_b");
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", USER_A);
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", USER_B);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("UPDATE user SET avatar = NULL WHERE id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?)", USER_A, USER_B);
        for (Path file : createdFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // 清理失败不影响测试结论，目录位于 build/ 下会被 maven clean 处理
            }
        }
        createdFiles.clear();
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, status, deleted) "
                        + "VALUES (?,?,?,?,1,0)",
                id, username, "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu", "头像测试用户");
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(role));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** MockMvc 的 getContentAsString() 默认按 ISO-8859-1 解码，中文会乱码，这里显式按 UTF-8 读字节 */
    private String message(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private MvcResult upload(String filename, String contentType, byte[] bytes, boolean withToken) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, bytes);
        var request = multipart("/api/v1/users/me/avatar").file(file);
        if (withToken) {
            request.header("Authorization", token(USER_A, RoleCode.USER));
        }
        return mockMvc.perform(request).andReturn();
    }

    private String uploadOk(String filename, String contentType, byte[] bytes, String expectExtension) throws Exception {
        MvcResult result = upload(filename, contentType, bytes, true);
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode data = json(result).path("data");
        String url = data.path("avatarUrl").asText();
        assertTrue(url.startsWith("/uploads/avatars/"), "应返回相对 URL：" + url);
        assertTrue(url.endsWith("." + expectExtension), "扩展名应规范化为 " + expectExtension + "：" + url);

        String filenameOnDisk = url.substring(url.lastIndexOf('/') + 1);
        Path saved = AVATAR_DIR.resolve(filenameOnDisk).normalize();
        createdFiles.add(saved);
        assertTrue(Files.exists(saved), "文件应落盘到 uploads/avatars");
        assertArrayEquals(bytes, Files.readAllBytes(saved), "落盘内容应与上传内容一致");

        // 数据库只保存相对 URL，不能存绝对路径或 Base64
        assertEquals(url, userMapper.selectById(USER_A).getAvatar());
        assertFalse(url.contains(":"), "URL 不应是磁盘绝对路径");
        return url;
    }

    @Test
    void upload_png_success() throws Exception {
        String url = uploadOk("avatar.PNG", "image/png", PNG_MAGIC, "png");
        assertTrue(url.matches("/uploads/avatars/[0-9a-f]{32}\\.png"), "文件名应为 32 位随机十六进制：" + url);
    }

    @Test
    void upload_jpg_success_normalizedToJpeg() throws Exception {
        // .jpg 与 image/jpg 都应被接受，落盘扩展名统一为 .jpeg
        uploadOk("photo.jpg", "image/jpg", JPEG_MAGIC, "jpeg");
    }

    @Test
    void upload_webp_success() throws Exception {
        uploadOk("banner.webp", "image/webp", WEBP_MAGIC, "webp");
    }

    @Test
    void upload_twice_generatesDifferentFilenames() throws Exception {
        String first = uploadOk("a.png", "image/png", PNG_MAGIC, "png");
        String second = uploadOk("a.png", "image/png", PNG_MAGIC, "png");
        assertFalse(first.equals(second), "UUID 随机文件名，重复上传不能覆盖旧文件");
    }

    @Test
    void upload_over2MB_badRequest() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(big, (byte) 0x41);
        MvcResult result = upload("big.png", "image/png", big, true);
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(message(result).contains("2MB"));
        assertNull(userMapper.selectById(USER_A).getAvatar(), "失败时不应改动数据库");
    }

    @Test
    void upload_unsupportedFormat_badRequest() throws Exception {
        MvcResult gif = upload("a.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII), true);
        assertEquals(400, gif.getResponse().getStatus());
        MvcResult svg = upload("a.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8), true);
        assertEquals(400, svg.getResponse().getStatus());
        assertNull(userMapper.selectById(USER_A).getAvatar());
    }

    @Test
    void upload_exeDisguisedAsPng_badRequest() throws Exception {
        // Content-Type 与扩展名都伪装成 PNG，但文件头是 MZ（可执行文件）
        byte[] fake = "MZ\u0090\u0000\u0003".getBytes(StandardCharsets.ISO_8859_1);
        MvcResult result = upload("virus.png", "image/png", fake, true);
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(message(result).contains("文件内容不是有效的"));
        assertNull(userMapper.selectById(USER_A).getAvatar());
    }

    @Test
    void upload_extensionMismatch_badRequest() throws Exception {
        // 真实是 PNG，却起名 .jpg：扩展名与魔数不一致必须拒绝
        MvcResult result = upload("fake.jpg", "image/png", PNG_MAGIC, true);
        assertEquals(400, result.getResponse().getStatus());
        assertNull(userMapper.selectById(USER_A).getAvatar());
    }

    @Test
    void upload_emptyFile_badRequest() throws Exception {
        MvcResult result = upload("empty.png", "image/png", new byte[0], true);
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(message(result).contains("请选择要上传的图片"));
    }

    @Test
    void upload_noToken_unauthorized() throws Exception {
        MvcResult result = upload("a.png", "image/png", PNG_MAGIC, false);
        assertEquals(401, result.getResponse().getStatus());
        assertNull(userMapper.selectById(USER_A).getAvatar());
    }

    @Test
    void upload_ignoresUserIdParam_cannotModifyOtherUser() throws Exception {
        // 请求夹带 userId=USER_B：接口没有该参数，身份只从 JWT 取
        MockMultipartFile file = new MockMultipartFile("file", "mine.png", "image/png", PNG_MAGIC);
        MvcResult result = mockMvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(file)
                        .param("userId", String.valueOf(USER_B))
                        .header("Authorization", token(USER_A, RoleCode.USER)))
                .andExpect(status().isOk()).andReturn();
        String url = json(result).path("data").path("avatarUrl").asText();
        createdFiles.add(AVATAR_DIR.resolve(url.substring(url.lastIndexOf('/') + 1)));

        assertNotNull(userMapper.selectById(USER_A).getAvatar(), "当前登录用户头像应更新");
        assertNull(userMapper.selectById(USER_B).getAvatar(), "不能修改其他用户头像");
    }

    @Test
    void uploadedFile_isPubliclyReadable() throws Exception {
        String url = uploadOk("view.png", "image/png", PNG_MAGIC, "png");
        MvcResult view = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        assertArrayEquals(PNG_MAGIC, Arrays.copyOf(view.getResponse().getContentAsByteArray(), PNG_MAGIC.length));
        assertTrue(view.getResponse().getContentType().contains(MediaType.IMAGE_PNG_VALUE),
                "静态资源应返回图片类型：" + view.getResponse().getContentType());
    }
}
