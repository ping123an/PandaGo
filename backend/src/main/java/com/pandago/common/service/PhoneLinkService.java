package com.pandago.common.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pandago.merchant.entity.MerchantProfile;
import com.pandago.merchant.mapper.MerchantProfileMapper;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 商家账号手机号联动服务。
 *
 * <p>业务背景：同一个商家在「个人中心」看到的是 user.phone，在「商家资料」看到的是
 * merchant_profile.contact_phone。两处都是真实字段，但用户理解上是同一个人同一个手机号，
 * 因此商家角色的手机号修改需要双向同步，避免两处显示不同。</p>
 *
 * <p>边界：
 * <ol>
 *   <li>只对拥有 MERCHANT 角色的账号做同步，普通用户的手机号不写入任何店铺字段；</li>
 *   <li>商家账号在个人中心和商家资料维护的是同一个联系电话，统一限定为大陆手机号
 *       （1[3-9]xxxxxxxx），填写和清空都双向同步；</li>
 *   <li>固话分支已移除，避免平台只显示一个联系电话列时再次出现两处值不一致。</li>
 * </ol>
 *
 * <p>调用方必须处于事务中：两次 UPDATE 要么都成功，要么都回滚，不允许出现只改了一边的中间状态。</p>
 */
@Component
@RequiredArgsConstructor
public class PhoneLinkService {

    private static final Pattern MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserMapper userMapper;
    private final MerchantProfileMapper merchantProfileMapper;

    /** 判断是否为可双向同步的大陆手机号 */
    public boolean isSyncableMobile(String phone) {
        String value = blankToNull(phone);
        return value != null && MOBILE.matcher(value).matches();
    }

    /** 个人中心手机号 → 店铺联系电话；清空间步清空。由调用方保证账号拥有 MERCHANT 角色。 */
    public void syncUserPhoneToStore(Long userId, String phone) {
        LambdaUpdateWrapper<MerchantProfile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MerchantProfile::getUserId, userId)
                .set(MerchantProfile::getContactPhone, blankToNull(phone));
        merchantProfileMapper.update(null, wrapper);
    }

    /**
     * 店铺联系电话 → 个人中心手机号。
     *
     * @return true 表示已同步；false 表示传入值不是手机号（DTO 层应已拦截），保留原值不写脏数据
     */
    public boolean syncStorePhoneToUser(Long userId, String contactPhone) {
        String phone = blankToNull(contactPhone);
        if (phone != null && !MOBILE.matcher(phone).matches()) {
            return false;
        }
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId).set(User::getPhone, phone);
        userMapper.update(null, wrapper);
        return true;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
