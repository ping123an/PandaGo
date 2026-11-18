package com.pandago;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 2 分类/商品接口集成测试。
 *
 * <p>阶段 3 起管理端改用 JWT + ADMIN 角色鉴权，测试通过 JwtTokenProvider 直接签发
 * 一枚 ADMIN Token 注入 Authorization 头（无需真实管理员账号）。</p>
 *
 * <p>@Transactional 让每个测试结束后回滚，测试产生的数据不污染初始化数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // 直接签发 ADMIN Token，模拟管理员身份
        adminToken = "Bearer " + jwtTokenProvider.generateToken(999L, "test-admin", List.of(RoleCode.ADMIN));
    }

    @Test
    void categoryListContainsSeededData() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("手机数码"))
                .andExpect(jsonPath("$.data[0].children.length()").value(2));
    }

    @Test
    void categoryCreateUpdateAndQuery() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"name\":\"集成测试分类\",\"sort\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").asLong();
        assertTrue(id > 0);

        mockMvc.perform(put("/api/v1/admin/categories/{id}", id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"集成测试分类已改名\",\"sort\":88,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == '集成测试分类已改名')]").exists());

        mockMvc.perform(put("/api/v1/admin/categories/{id}/status", id)
                        .param("status", "0")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void productCreateUpdateAndAdminDetail() throws Exception {
        long categoryId = objectMapper.readTree(mockMvc.perform(get("/api/v1/categories"))
                        .andReturn().getResponse().getContentAsString())
                .path("data").get(0).path("id").asLong();

        String created = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"集成测试商品\",\"subtitle\":\"测试副标题\""
                                + ",\"mainImage\":\"/images/panda-product.svg\""
                                + ",\"price\":12.34,\"stock\":5,\"sort\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").asLong();
        assertTrue(id > 0);

        mockMvc.perform(get("/api/v1/admin/products/{id}", id)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("集成测试商品"))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));

        mockMvc.perform(put("/api/v1/admin/products/{id}", id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":" + categoryId
                                + ",\"name\":\"集成测试商品已改名\",\"mainImage\":\"/images/panda-product.svg\""
                                + ",\"price\":23.45,\"stock\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/admin/products/{id}", id)
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.name").value("集成测试商品已改名"))
                .andExpect(jsonPath("$.data.price").value(23.45));
    }

    @Test
    void productPageAndDetail() throws Exception {
        // 初始化演示商品可能随阶段增加，断言下限与分页计算，避免硬编码总数
        byte[] body = mockMvc.perform(get("/api/v1/products").param("pageNum", "1").param("pageSize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list.length()").value(4))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode page = objectMapper.readTree(body).path("data");
        long total = page.path("total").asLong();
        assertTrue(total >= 10, "演示商品至少应有 10 条，实际 " + total);
        assertEquals((total + 3) / 4, page.path("pages").asLong());

        Product first = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, ProductStatus.ON_SALE.getValue())
                        .orderByAsc(Product::getId)
                        .last("LIMIT 1"))
                .get(0);
        assertEquals("ON_SALE", first.getStatus());

        mockMvc.perform(get("/api/v1/products/{id}", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first.getId()))
                .andExpect(jsonPath("$.data.categoryName").exists());
    }

    @Test
    void nonExistingProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidPaginationReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("pageNum", "0").param("pageSize", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/products").param("pageNum", "1").param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void sortInjectionIsRejectedByWhitelist() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sort", "p.price; DROP TABLE product")
                        .param("order", "asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void adminEndpointRequiresAuth() throws Exception {
        // 无 Token 访问管理端：401
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无Token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
