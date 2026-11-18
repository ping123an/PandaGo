package com.pandago.product.service;

import com.pandago.common.api.PageResult;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductManagementVO;

/** 商家侧商品管理：所有方法都在 Service 内校验 MERCHANT 角色与商品归属。 */
public interface MerchantProductService {

    PageResult<ProductManagementVO> listMyProducts(ProductManagementQueryDTO query,
                                                    long pageNum, long pageSize);

    ProductDetailVO detail(Long id);

    Long create(ProductCreateDTO dto);

    void update(Long id, ProductUpdateDTO dto);

    /** 上架/下架：只允许操作本人商品，未审核通过不能上架 */
    void updateStatus(Long id, String status);

    /** 提交审核：仅本人商品；已审核通过的商品不能重复提交 */
    void submitForAudit(Long id);
}
