package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.product.entity.Product;
import com.pandago.product.entity.ProductImage;
import com.pandago.product.mapper.ProductImageMapper;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台管理端商品审核 / 商品管理状态逻辑专项测试。
 *
 * <p>覆盖：三种审核状态筛选、多条件组合筛选、分类筛选、参数校验、
 * 上架前审核校验、审核流转与幂等、驳回原因、审核人留痕、角色权限、管理端详情。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminProductAuditTests {

    private static final long ADMIN_ID = 91001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImageMapper productImageMapper;

    private String admin() {
        return "Bearer " + jwtTokenProvider.generateToken(ADMIN_ID, "admin-audit-tests", List.of(RoleCode.ADMIN));
    }

    private String merchant() {
        return "Bearer " + jwtTokenProvider.generateToken(91002L, "merchant-audit-tests", List.of(RoleCode.MERCHANT));
    }

    private String user() {
        return "Bearer " + jwtTokenProvider.generateToken(91003L, "user-audit-tests", List.of(RoleCode.USER));
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long secondCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode roots = objectMapper.readTree(body).path("data");
        return roots.size() > 1
                ? roots.get(1).path("id").asLong()
                : roots.get(0).path("children").get(0).path("id").asLong();
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long insertProduct(long categoryId, String name, String status, String auditStatus) {
        Product p = new Product();
        p.setCategoryId(categoryId);
        p.setMerchantId(0L);
        p.setName(name);
        p.setMainImage("/images/panda-product.svg");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(20);
        p.setSales(0);
        p.setStatus(status);
        p.setAuditStatus(auditStatus);
        p.setDescription("商品审核测试");
        p.setSort(0);
        productMapper.insert(p);
        ProductImage image = new ProductImage();
        image.setProductId(p.getId());
        image.setUrl("/images/panda-product.svg");
        image.setSort(1);
        productImageMapper.insert(image);
        return p.getId();
    }

    private JsonNode adminPage(String query) throws Exception {
        byte[] body = mockMvc.perform(get("/api/v1/admin/products" + query).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode findInList(JsonNode data, long id) {
        for (JsonNode node : data.path("list")) {
            if (node.path("id").asLong() == id) return node;
        }
        return null;
    }

    private void expectStatus(long id, String status, int expectedHttpStatus) throws Exception {
        mockMvc.perform(put("/api/v1/admin/products/{id}/status", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(expectedHttpStatus));
    }

    // ---------- 筛选 ----------

    @Test
    void adminPageNoFilterIncludesAllAuditStatuses() throws Exception {
        long cat = firstCategoryId();
        long pending = insertProduct(cat, uniqueName("AP-全部待审"), "OFF_SALE", AuditStatus.PENDING.getValue());
        long approved = insertProduct(cat, uniqueName("AP-全部通过"), "ON_SALE", AuditStatus.APPROVED.getValue());
        long rejected = insertProduct(cat, uniqueName("AP-全部驳回"), "OFF_SALE", AuditStatus.REJECTED.getValue());

        JsonNode data = adminPage("?pageSize=50");
        assertNotNull(findInList(data, pending));
        assertNotNull(findInList(data, approved));
        assertNotNull(findInList(data, rejected));
    }

    @Test
    void adminPageFilterPendingOnlyReturnsPending() throws Exception {
        long cat = firstCategoryId();
        long pending = insertProduct(cat, uniqueName("AP-筛待审"), "OFF_SALE", AuditStatus.PENDING.getValue());
        long approved = insertProduct(cat, uniqueName("AP-筛通过"), "ON_SALE", AuditStatus.APPROVED.getValue());

        JsonNode data = adminPage("?auditStatus=PENDING&pageSize=50");
        assertNotNull(findInList(data, pending));
        assertEquals(null, findInList(data, approved));
        for (JsonNode node : data.path("list")) {
            assertEquals("PENDING", node.path("auditStatus").asText());
        }
    }

    @Test
    void adminPageFilterApprovedOnlyReturnsApproved() throws Exception {
        long cat = firstCategoryId();
        long pending = insertProduct(cat, uniqueName("AP-通过筛待"), "OFF_SALE", AuditStatus.PENDING.getValue());
        long approved = insertProduct(cat, uniqueName("AP-通过筛通"), "ON_SALE", AuditStatus.APPROVED.getValue());

        JsonNode data = adminPage("?auditStatus=APPROVED&pageSize=50");
        assertNotNull(findInList(data, approved));
        assertEquals(null, findInList(data, pending));
    }

    @Test
    void adminPageFilterRejectedOnlyReturnsRejected() throws Exception {
        long cat = firstCategoryId();
        long rejected = insertProduct(cat, uniqueName("AP-筛驳回"), "OFF_SALE", AuditStatus.REJECTED.getValue());
        long approved = insertProduct(cat, uniqueName("AP-驳回筛通"), "ON_SALE", AuditStatus.APPROVED.getValue());

        JsonNode data = adminPage("?auditStatus=REJECTED&pageSize=50");
        assertNotNull(findInList(data, rejected));
        assertEquals(null, findInList(data, approved));
    }

    @Test
    void adminPageCombinedFiltersMatchOnlyTarget() throws Exception {
        long cat = firstCategoryId();
        String name = uniqueName("AP-组合");
        long target = insertProduct(cat, name, "OFF_SALE", AuditStatus.PENDING.getValue());
        insertProduct(cat, name + "-其他", "ON_SALE", AuditStatus.PENDING.getValue());
        insertProduct(cat, name + "-另状态", "OFF_SALE", AuditStatus.APPROVED.getValue());

        JsonNode data = adminPage("?keyword=" + name
                + "&status=OFF_SALE&auditStatus=PENDING&categoryId=" + cat + "&pageSize=50");
        assertNotNull(findInList(data, target), "组合筛选应命中目标商品");
        assertEquals(1, data.path("list").size(), "关键词应排除名称带后缀的其他商品");
    }

    @Test
    void adminPageCategoryFilterExcludesOtherCategories() throws Exception {
        long catA = firstCategoryId();
        long catB = secondCategoryId();
        long inA = insertProduct(catA, uniqueName("AP-分类A"), "OFF_SALE", AuditStatus.PENDING.getValue());
        long inB = insertProduct(catB, uniqueName("AP-分类B"), "OFF_SALE", AuditStatus.PENDING.getValue());

        JsonNode data = adminPage("?categoryId=" + catB + "&pageSize=50");
        assertNotNull(findInList(data, inB));
        assertEquals(null, findInList(data, inA));
    }

    @Test
    void adminPagePaginationReturnsRequestedPage() throws Exception {
        long cat = firstCategoryId();
        insertProduct(cat, uniqueName("AP-分页"), "OFF_SALE", AuditStatus.PENDING.getValue());

        JsonNode page1 = adminPage("?pageNum=1&pageSize=1");
        assertEquals(1, page1.path("list").size());
        assertTrue(page1.path("total").asLong() > 1);
    }

    @Test
    void adminPageReturnsMerchantAndAuditFields() throws Exception {
        long cat = firstCategoryId();
        long id = insertProduct(cat, uniqueName("AP-字段"), "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        JsonNode row = findInList(adminPage("?auditStatus=APPROVED&pageSize=50"), id);
        assertNotNull(row);
        assertEquals("平台自营", row.path("merchantName").asText());
        assertEquals(ADMIN_ID, row.path("auditBy").asLong());
        assertFalse(row.path("auditTime").isMissingNode(), "应返回审核时间");
    }

    @Test
    void adminPageInvalidAuditStatusRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").param("auditStatus", "WAITING")
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminPageInvalidSaleStatusRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").param("status", "SALE")
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest());
    }

    // ---------- 上架前审核校验 ----------

    @Test
    void pendingProductCannotBePutOnSale() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-待审上架"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/status", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品待审核，不能上架"));

        assertEquals("OFF_SALE", productMapper.selectById(id).getStatus());
    }

    @Test
    void rejectedProductCannotBePutOnSale() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-驳回上架"),
                "OFF_SALE", AuditStatus.REJECTED.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/status", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ON_SALE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品审核已驳回，不能上架"));
    }

    @Test
    void approvedProductCanOnOffAndRelist() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-已通过上下架"),
                "OFF_SALE", AuditStatus.APPROVED.getValue());

        expectStatus(id, "ON_SALE", 200);
        assertEquals("ON_SALE", productMapper.selectById(id).getStatus());
        expectStatus(id, "OFF_SALE", 200);
        assertEquals("OFF_SALE", productMapper.selectById(id).getStatus());
        expectStatus(id, "ON_SALE", 200);
        assertEquals("ON_SALE", productMapper.selectById(id).getStatus());
    }

    // ---------- 审核流转 ----------

    @Test
    void approvePendingRecordsAuditorAndTime() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-通过留痕"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        Product updated = productMapper.selectById(id);
        assertEquals("APPROVED", updated.getAuditStatus());
        assertEquals(ADMIN_ID, updated.getAuditBy());
        assertNotNull(updated.getAuditTime());
        assertEquals(null, updated.getAuditReason());
        assertEquals("OFF_SALE", updated.getStatus(), "审核通过不会自动上架");
    }

    @Test
    void rejectWithoutReasonReturnsBadRequest() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-空原因"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"REJECTED\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("驳回必须填写原因"));

        assertEquals("PENDING", productMapper.selectById(id).getAuditStatus());
    }

    @Test
    void rejectWithReasonForcesOffSaleAndRecordsReason() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-驳回留痕"),
                "ON_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"REJECTED\",\"reason\":\"图片不清晰\"}"))
                .andExpect(status().isOk());

        Product updated = productMapper.selectById(id);
        assertEquals("REJECTED", updated.getAuditStatus());
        assertEquals("OFF_SALE", updated.getStatus(), "驳回必须强制下架");
        assertEquals("图片不清晰", updated.getAuditReason());
        assertEquals(ADMIN_ID, updated.getAuditBy());
        assertNotNull(updated.getAuditTime());
    }

    @Test
    void repeatedAuditReturnsConflict() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-重复审核"),
                "OFF_SALE", AuditStatus.PENDING.getValue());
        String body = "{\"auditStatus\":\"APPROVED\"}";

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品当前已是已通过状态，请勿重复审核"));
    }

    @Test
    void rejectedProductCannotBeDirectlyApproved() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-驳回直通"),
                "OFF_SALE", AuditStatus.REJECTED.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("商品已被驳回，需商家重新提交审核后才能通过"));

        assertEquals("REJECTED", productMapper.selectById(id).getAuditStatus());
    }

    @Test
    void rejectedProductCanBeAuditedAgainAfterResubmit() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-重新提交"),
                "OFF_SALE", AuditStatus.REJECTED.getValue());

        // 模拟商家重新提交审核：回到 PENDING
        Product product = productMapper.selectById(id);
        product.setAuditStatus(AuditStatus.PENDING.getValue());
        product.setAuditReason(null);
        productMapper.updateById(product);

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk());
        assertEquals("APPROVED", productMapper.selectById(id).getAuditStatus());
    }

    // ---------- 权限与详情 ----------

    @Test
    void merchantCannotAuditProduct() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-商家越权"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", merchant())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        assertEquals("PENDING", productMapper.selectById(id).getAuditStatus());
    }

    @Test
    void normalUserCannotAuditProduct() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-用户越权"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        mockMvc.perform(put("/api/v1/admin/products/{id}/audit", id)
                        .header("Authorization", user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"auditStatus\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDetailShowsStockAndImagesForPendingProduct() throws Exception {
        long id = insertProduct(firstCategoryId(), uniqueName("AP-详情"),
                "OFF_SALE", AuditStatus.PENDING.getValue());

        String body = mockMvc.perform(get("/api/v1/admin/products/{id}", id)
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(id, data.path("id").asLong());
        assertEquals(20, data.path("stock").asInt(), "管理端详情应显示具体库存");
        assertEquals("PENDING", data.path("auditStatus").asText());
        assertFalse(data.path("images").isEmpty(), "详情应包含图片列表");
    }

    @Test
    void adminPageKeywordOnlyMatchesProductName() throws Exception {
        long cat = firstCategoryId();
        String name = uniqueName("AP-关键词");
        long target = insertProduct(cat, name, "OFF_SALE", AuditStatus.PENDING.getValue());
        insertProduct(cat, uniqueName("AP-其他词"), "OFF_SALE", AuditStatus.PENDING.getValue());

        JsonNode data = adminPage("?keyword=" + name + "&pageSize=50");
        assertNotNull(findInList(data, target));
        assertEquals(1, data.path("list").size());
    }
}
