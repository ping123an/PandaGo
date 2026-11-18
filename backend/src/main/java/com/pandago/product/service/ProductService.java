package com.pandago.product.service;

import com.pandago.common.api.PageResult;
import com.pandago.product.dto.ProductAuditDTO;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductQueryDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductListVO;
import com.pandago.product.vo.ProductManagementVO;

import java.util.List;

public interface ProductService {

    /** 用户端分页查询，仅返回审核通过且上架的商品 */
    PageResult<ProductListVO> page(ProductQueryDTO query, long pageNum, long pageSize);

    /** 首页热门商品，与用户端列表使用同一可见性规则 */
    List<ProductListVO> hot(int limit);

    /** 用户端详情，仅返回审核通过且上架的商品 */
    ProductDetailVO detail(Long id);

    /** 管理端详情，可查看待审核/已下架商品 */
    ProductDetailVO adminDetail(Long id);

    /** 管理端商品分页；merchantId 为 null 时查询全平台 */
    PageResult<ProductManagementVO> adminPage(ProductManagementQueryDTO query, long pageNum, long pageSize);

    /** 管理员审核商品：APPROVED 或 REJECTED */
    void audit(Long id, ProductAuditDTO dto);

    /** 管理端基础维护（merchantId 固定 0，仅供平台演示数据，不替代商家端） */
    Long create(ProductCreateDTO dto);

    void update(Long id, ProductUpdateDTO dto);

    /** 上架/下架；未审核通过的商品不允许上架 */
    void updateStatus(Long id, String status);
}
