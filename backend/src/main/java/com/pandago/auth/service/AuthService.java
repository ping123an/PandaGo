package com.pandago.auth.service;

import com.pandago.auth.dto.LoginDTO;
import com.pandago.auth.dto.RegisterDTO;
import com.pandago.auth.dto.UserProfileUpdateDTO;
import com.pandago.auth.vo.LoginVO;
import com.pandago.auth.vo.UserInfoVO;

/** 认证业务接口。 */
public interface AuthService {

    /** 注册用户，返回新用户 id。角色固定分配 USER。 */
    Long register(RegisterDTO dto);

    /** 账号密码登录，校验成功后签发 JWT。 */
    LoginVO login(LoginDTO dto);

    /** 获取当前登录用户信息（身份来自 SecurityContext）。 */
    UserInfoVO currentUser();

    /** 修改当前登录用户的个人资料（白名单：昵称 / 手机号），返回更新后的资料。 */
    UserInfoVO updateMyProfile(UserProfileUpdateDTO dto);
}
