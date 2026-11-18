package com.pandago.merchant.service;

import com.pandago.merchant.vo.MerchantDashboardVO;

/** 商家工作台统计业务入口。 */
public interface MerchantDashboardService {

    /** 当前登录商家的经营统计（商品/订单/销售额 + 最近订单与商品） */
    MerchantDashboardVO dashboard();
}
