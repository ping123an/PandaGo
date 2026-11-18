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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 库存可见性验收：用户端不暴露具体库存数量，只提供 stockStatus；
 * 商家端/管理端仍能看到具体库存。所有库存扣减/防超卖逻辑不受影响，仍由后端校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockVisibilityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ProductMapper productMapper;

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long insertProduct(int stock) throws Exception {
        Product p = new Product();
        p.setCategoryId(firstCategoryId());
        p.setMerchantId(90001L);
        p.setName("库存可见性-" + UUID.randomUUID().toString().substring(0, 8));
        p.setMainImage("/images/panda-product.svg");
        p.setPrice(new BigDecimal("99.00"));
        p.setStock(stock);
        p.setSales(10);
        p.setStatus(ProductStatus.ON_SALE.getValue());
        p.setAuditStatus(AuditStatus.APPROVED.getValue());
        p.setSort(0);
        productMapper.insert(p);
        return p.getId();
    }

    /** 用户端列表卡片：不返回 stock 数字，返回 stockStatus=IN_STOCK */
    @Test
    void userListHidesStockNumberButExposesStatus() throws Exception {
        long id = insertProduct(37);
        String resp = mockMvc.perform(get("/api/v1/products").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode item = null;
        for (JsonNode n : objectMapper.readTree(resp).path("data").path("list")) {
            if (n.path("id").asLong() == id) { item = n; break; }
        }
        assertTrue(item != null, "应能查到测试商品");
        assertTrue(item.path("stock").isMissingNode() || item.path("stock").isNull(),
                "用户端卡片不应返回具体库存数量");
        assertEquals("IN_STOCK", item.path("stockStatus").asText());
    }

    /** 用户端详情：库存数字被脱敏为空，stockStatus=IN_STOCK */
    @Test
    void userDetailMasksStockNumber() throws Exception {
        long id = insertProduct(42);
        String resp = mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        assertTrue(data.path("stock").isMissingNode() || data.path("stock").isNull(),
                "用户端详情不应返回具体库存数量");
        assertEquals("IN_STOCK", data.path("stockStatus").asText());
    }

    /** 用户端详情：库存为 0 时 stockStatus=OUT_OF_STOCK */
    @Test
    void userDetailExposesOutOfStockStatus() throws Exception {
        long id = insertProduct(0);
        String resp = mockMvc.perform(get("/api/v1/products/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        assertTrue(data.path("stock").isMissingNode() || data.path("stock").isNull());
        assertEquals("OUT_OF_STOCK", data.path("stockStatus").asText());
    }

    /** 管理端详情：仍能看到具体库存数量 */
    @Test
    void adminDetailStillShowsStockNumber() throws Exception {
        long id = insertProduct(88);
        String token = "Bearer " + jwtTokenProvider.generateToken(90002L, "admin-stock", List.of(RoleCode.ADMIN));
        String resp = mockMvc.perform(get("/api/v1/admin/products/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(resp).path("data");
        assertEquals(88, data.path("stock").asInt(), "管理端应能看到具体库存");
    }

    /** 商家端列表：仍能看到具体库存数量 */
    @Test
    void merchantListStillShowsStockNumber() throws Exception {
        long id = insertProduct(66);
        String token = "Bearer " + jwtTokenProvider.generateToken(90001L, "merchant-stock", List.of(RoleCode.MERCHANT));
        String resp = mockMvc.perform(get("/api/v1/merchant/products?pageSize=50").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(resp).path("data").path("list");
        boolean found = false;
        for (JsonNode n : list) {
            if (n.path("id").asLong() == id) {
                assertEquals(66, n.path("stock").asInt(), "商家端应能看到具体库存");
                found = true;
                break;
            }
        }
        assertTrue(found, "商家列表应能查到测试商品");
    }
}
