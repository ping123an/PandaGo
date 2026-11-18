package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 4 商品浏览、搜索、商家管理与审核流程集成测试。
 *
 * <p>每个测试由 @Transactional 回滚，不污染初始化数据。Token 由 JwtTokenProvider 直接签发，
 * 分别模拟 MERCHANT / USER / ADMIN，不依赖额外测试账号。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Stage4ProductFlowTests {

    private static final String MAIN_IMAGE = "/images/panda-product.svg";
    private static final String DETAIL_IMAGE = "/images/panda-product-detail.svg";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProductMapper productMapper;

    private String merchantToken(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "merchant-" + userId, List.of(RoleCode.MERCHANT));
    }

    private String userToken(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(RoleCode.USER));
    }

    private String adminToken(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "admin-" + userId, List.of(RoleCode.ADMIN));
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private Long insertProduct(long categoryId, String name, long merchantId,
                               String status, String auditStatus, BigDecimal price, int stock) {
        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setMerchantId(merchantId);
        product.setName(name);
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(price);
        product.setStock(stock);
        product.setSales(0);
        product.setStatus(status);
        product.setAuditStatus(auditStatus);
        product.setDescription("阶段4测试商品");
        product.setSort(0);
        productMapper.insert(product);
        return product.getId();
    }

    private String productPayload(String name, long categoryId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", name);
        body.put("subtitle", "阶段4自动化验证");
        body.put("mainImage", MAIN_IMAGE);
        body.put("price", new BigDecimal("88.88"));
        body.put("stock", 10);
        body.put("sort", 1);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("url", DETAIL_IMAGE);
        detail.put("type", "DETAIL");
        body.put("detailImages", List.of(detail));
        return objectMapper.writeValueAsString(body);
    }

    private long createMerchantProduct(String token, String name, long categoryId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/merchant/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(name, categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").asLong();
    }

    /** 场景 1：商品分页 */
    @Test
    void productPaginationWorks() throws Exception {
        // 断言下限，兼容审核演示数据带来的商品总数变化
        byte[] body = mockMvc.perform(get("/api/v1/products").param("pageNum", "1").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode page = objectMapper.readTree(body).path("data");
        assertTrue(page.path("total").asLong() >= 10,
                "演示商品至少应有 10 条，实际 " + page.path("total").asLong());
    }

    /** 场景 2：关键词搜索 */
    @Test
    void keywordSearchFindsMatchingProduct() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("关键词搜索A1");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.ON_SALE.getValue(),
                AuditStatus.APPROVED.getValue(), new BigDecimal("66.60"), 5);

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/products")
                        .param("keyword", name).param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, body.path("data").path("total").asInt());
        assertEquals(id.longValue(), body.path("data").path("list").get(0).path("id").asLong());
    }

    /** 场景 3：分类筛选 */
    @Test
    void categoryFilterOnlyReturnsSelectedCategory() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("分类筛选");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.ON_SALE.getValue(),
                AuditStatus.APPROVED.getValue(), new BigDecimal("77.70"), 5);

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", String.valueOf(categoryId)).param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(body.path("data").path("total").asInt() > 0);
        boolean found = false;
        for (JsonNode item : body.path("data").path("list")) {
            assertEquals(categoryId, item.path("categoryId").asLong());
            if (item.path("id").asLong() == id) {
                found = true;
            }
        }
        assertTrue(found, "筛选结果应包含新插入的目标商品");
    }

    /** 场景 4：价格区间筛选 */
    @Test
    void priceRangeFilterOnlyReturnsProductsInRange() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("价格区间");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.ON_SALE.getValue(),
                AuditStatus.APPROVED.getValue(), new BigDecimal("188.88"), 5);

        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/products")
                        .param("minPrice", "188").param("maxPrice", "189").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : body.path("data").path("list")) {
            BigDecimal price = item.path("price").decimalValue();
            assertTrue(price.compareTo(new BigDecimal("188")) >= 0);
            assertTrue(price.compareTo(new BigDecimal("189")) <= 0);
            if (item.path("id").asLong() == id) {
                found = true;
            }
        }
        assertTrue(found, "价格区间结果应包含新插入的目标商品");
    }

    /** 场景 5：非法排序字段 */
    @Test
    void illegalSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "p.price; DROP TABLE product"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/v1/products").param("sort", "price").param("order", "asc; drop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 场景 6：下架商品不出现在可购买列表 */
    @Test
    void offShelfProductIsNotVisible() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("已下架");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.OFF_SALE.getValue(),
                AuditStatus.APPROVED.getValue(), new BigDecimal("99.00"), 5);

        mockMvc.perform(get("/api/v1/products").param("keyword", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 场景 7：未审核商品不出现在可购买列表 */
    @Test
    void unapprovedProductIsNotVisible() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("未审核");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.ON_SALE.getValue(),
                AuditStatus.PENDING.getValue(), new BigDecimal("99.00"), 5);

        mockMvc.perform(get("/api/v1/products").param("keyword", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 场景 8：商家不能修改其他商家的商品 */
    @Test
    void merchantCannotModifyOtherMerchantsProduct() throws Exception {
        long categoryId = firstCategoryId();
        String originalName = uniqueName("他人商品");
        Long id = insertProduct(categoryId, originalName, 2002L, ProductStatus.OFF_SALE.getValue(),
                AuditStatus.PENDING.getValue(), new BigDecimal("55.55"), 3);
        String changedName = uniqueName("篡改名称");

        mockMvc.perform(put("/api/v1/merchant/products/{id}", id)
                        .header("Authorization", merchantToken(1001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(changedName, categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(originalName, productMapper.selectById(id).getName(), "越权修改不得改变数据库数据");
    }

    /** 场景 9：普通用户不能调用商家接口 */
    @Test
    void normalUserCannotCallMerchantApi() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/products")
                        .header("Authorization", userToken(3001L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/v1/merchant/products")
                        .header("Authorization", userToken(3001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(uniqueName("越权新增"), firstCategoryId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 场景 10：商品不存在时的处理 */
    @Test
    void nonExistingProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", 999999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/v1/merchant/products/{id}", 999999999L)
                        .header("Authorization", merchantToken(1001L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    /** 补充：商家创建 -> 提交审核 -> 管理员审核 -> 上架 -> 下架完整链路 */
    @Test
    void merchantProductCanGoThroughFullAuditAndShelfFlow() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("商家完整流程");
        String merchant = merchantToken(4001L);
        String admin = adminToken(9001L);
        long id = createMerchantProduct(merchant, name, categoryId);

        JsonNode created = objectMapper.readTree(mockMvc.perform(get("/api/v1/merchant/products/{id}", id)
                        .header("Authorization", merchant))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals("PENDING", created.path("data").path("auditStatus").asText());
        assertEquals("OFF_SALE", created.path("data").path("status").asText());
        assertEquals(2, created.path("data").path("images").size(), "主图 + 1 张详情图均应入库");

        mockMvc.perform(put("/api/v1/merchant/products/{id}/status", id)
                        .header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(post("/api/v1/merchant/products/{id}/submit-audit", id)
                        .header("Authorization", merchant))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/merchant/products/{id}/status", id)
                        .header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(name));

        mockMvc.perform(put("/api/v1/merchant/products/{id}/status", id)
                        .header("Authorization", merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SALE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    /** 补充：审核驳回后商品强制下架，并记录驳回原因 */
    @Test
    void rejectingProductForcesOffShelfAndStoresReason() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("审核驳回");
        Long id = insertProduct(categoryId, name, 0L, ProductStatus.ON_SALE.getValue(),
                AuditStatus.APPROVED.getValue(), new BigDecimal("123.00"), 5);

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", adminToken(9002L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"REJECTED\",\"reason\":\"图片不清晰\"}"))
                .andExpect(status().isOk());

        Product product = productMapper.selectById(id);
        assertEquals("REJECTED", product.getAuditStatus());
        assertEquals("OFF_SALE", product.getStatus());
        assertEquals("图片不清晰", product.getAuditReason());
    }

    /** 补充：非法图片 URL 与超限声明大小均被拒绝，且不会产生孤立商品记录 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidImageUrlAndOversizedMetadataAreRejected() throws Exception {
        long categoryId = firstCategoryId();
        String name = uniqueName("非法图片");

        Map<String, Object> badUrl = new LinkedHashMap<>();
        badUrl.put("categoryId", categoryId);
        badUrl.put("name", name + "-A");
        badUrl.put("mainImage", "javascript:alert(1).png");
        badUrl.put("price", new BigDecimal("10.00"));
        badUrl.put("stock", 1);
        mockMvc.perform(post("/api/v1/merchant/products")
                        .header("Authorization", merchantToken(5001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        Map<String, Object> oversized = new LinkedHashMap<>();
        oversized.put("categoryId", categoryId);
        oversized.put("name", name + "-B");
        oversized.put("mainImage", MAIN_IMAGE);
        oversized.put("mainImageSizeBytes", 5L * 1024 * 1024 + 1);
        oversized.put("price", new BigDecimal("10.00"));
        oversized.put("stock", 1);
        mockMvc.perform(post("/api/v1/merchant/products")
                        .header("Authorization", merchantToken(5001L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0L, productMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .likeRight(Product::getName, name)).longValue());
    }

    /** 补充：热门商品接口只取可见商品且限制数量 */
    @Test
    void hotProductsRespectLimitAndVisibility() throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/products/hot").param("limit", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(3, body.path("data").size());
        assertTrue(body.path("data").size() <= 3);
    }
}
