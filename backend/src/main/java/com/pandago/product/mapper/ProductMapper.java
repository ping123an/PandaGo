package com.pandago.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductQueryDTO;
import com.pandago.product.entity.Product;
import com.pandago.product.vo.ProductListVO;
import com.pandago.product.vo.ProductManagementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 用户端条件分页查询，SQL 写在 Mapper XML 中。
     * 分页插件识别 IPage 参数并自动追加 LIMIT 与 COUNT 查询。
     */
    List<ProductListVO> selectProductList(@Param("query") ProductQueryDTO query,
                                          @Param("page") IPage<ProductListVO> page);

    /** 首页热门商品：只取审核通过、上架、未删除的数据 */
    List<ProductListVO> selectHotProducts(@Param("limit") int limit);

    /**
     * 商家/管理员管理列表。query.merchantId 由服务端注入，商家端强制过滤本人商品；
     * 管理员端不传 merchantId，表示全平台。
     */
    List<ProductManagementVO> selectProductManagementList(
            @Param("query") ProductManagementQueryDTO query,
            @Param("page") IPage<ProductManagementVO> page);

    /**
     * 条件扣减库存（阶段 6）：一条原子 UPDATE 同时校验商品 ID、库存充足、上架、审核通过、未删除。
     *
     * <p>并发安全的关键：WHERE 条件由数据库在行锁下判断，不会出现"Java 先查到库存够、
     * 两个线程同时扣成负数"。销售量与库存同语句更新，避免前后不一致。</p>
     *
     * @return 受影响行数：1 表示扣减成功；0 表示库存不足或商品不可售（调用方必须抛异常回滚）
     */
    int deductStock(@Param("productId") Long productId,
                    @Param("quantity") int quantity);

    /**
     * 取消订单时恢复库存（阶段 7）。
     *
     * <p>只按商品 ID 回补，不再限制上架/审核状态：即使商品后来下架，
     * 该订单原先占用的库存也应归还。受影响行数为 1 才表示回补成功。</p>
     *
     * <p>本阶段按用户要求只恢复 stock，不改变 sales 统计口径；sales 仍记录历史成交动作。</p>
     */
    int restoreStock(@Param("productId") Long productId,
                     @Param("quantity") int quantity);
}
