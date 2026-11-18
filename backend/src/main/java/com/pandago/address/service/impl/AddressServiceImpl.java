package com.pandago.address.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pandago.address.dto.AddressCreateDTO;
import com.pandago.address.dto.AddressUpdateDTO;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.address.service.AddressService;
import com.pandago.address.vo.AddressVO;
import com.pandago.common.api.ResultCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 收货地址业务实现（阶段 5）。
 *
 * <p>核心规则：
 * 1. 所有查询/写入条件都带当前登录用户 id，操作他人地址统一返回 404；
 * 2. 一个用户最多一个默认地址：Service 在同一事务里"先清旧默认、再设新默认"，
 *    数据库层还有 default_flag 生成列唯一索引兜底；
 * 3. 删除默认地址后不自动指定新默认（PRD 未规定，见 docs/DATABASE.md 的方案说明），
 *    先把该地址 is_default 置 0 再逻辑删除，避免生成列唯一索引残留占用。</p>
 */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    @Override
    public List<AddressVO> listMyAddresses() {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        List<UserAddress> addresses = addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreatedAt));
        List<AddressVO> result = new ArrayList<>(addresses.size());
        for (UserAddress address : addresses) {
            result.add(toVO(address));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AddressCreateDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        boolean makeDefault = Boolean.TRUE.equals(dto.getIsDefault());

        if (makeDefault) {
            clearMyDefaults(userId);
        }
        UserAddress address = new UserAddress();
        applyFields(address, dto.getReceiverName(), dto.getPhone(), dto.getProvince(),
                dto.getCity(), dto.getDistrict(), dto.getDetailAddress());
        address.setUserId(userId);
        address.setIsDefault(makeDefault ? 1 : 0);
        try {
            addressMapper.insert(address);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT, "设置默认地址失败，请重试");
        }
        return address.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AddressUpdateDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        UserAddress address = requireMyAddress(userId, id);

        applyFields(address, dto.getReceiverName(), dto.getPhone(), dto.getProvince(),
                dto.getCity(), dto.getDistrict(), dto.getDetailAddress());
        if (Boolean.TRUE.equals(dto.getIsDefault())) {
            clearMyDefaults(userId);
            address.setIsDefault(1);
        } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
            address.setIsDefault(0);
        }
        try {
            addressMapper.updateById(address);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT, "设置默认地址失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        UserAddress address = requireMyAddress(userId, id);

        // 删除默认地址前先取消默认标记：否则生成列 default_flag 仍占用唯一索引，
        // 之后无法再把其他地址设为默认。PRD 未要求自动补位，这里不自动指定新默认。
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            address.setIsDefault(0);
            addressMapper.updateById(address);
        }
        addressMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        UserAddress target = requireMyAddress(userId, id);

        clearMyDefaults(userId);
        target.setIsDefault(1);
        try {
            addressMapper.updateById(target);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT, "设置默认地址失败，请重试");
        }
    }

    /** 同一事务内清掉我的所有默认标记，为新默认让位 */
    private void clearMyDefaults(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1)
                .set(UserAddress::getIsDefault, 0));
    }

    /** 他人地址与不存在地址统一 404，避免泄露资源是否存在 */
    private UserAddress requireMyAddress(Long userId, Long id) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return address;
    }

    private void applyFields(UserAddress address, String receiverName, String phone,
                             String province, String city, String district, String detailAddress) {
        address.setReceiverName(receiverName.trim());
        address.setPhone(phone.trim());
        address.setProvince(province.trim());
        address.setCity(city.trim());
        address.setDistrict(district.trim());
        address.setDetailAddress(detailAddress.trim());
    }

    private AddressVO toVO(UserAddress address) {
        AddressVO vo = new AddressVO();
        vo.setId(address.getId());
        vo.setReceiverName(address.getReceiverName());
        vo.setPhone(address.getPhone());
        vo.setProvince(address.getProvince());
        vo.setCity(address.getCity());
        vo.setDistrict(address.getDistrict());
        vo.setDetailAddress(address.getDetailAddress());
        vo.setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1);
        vo.setCreatedAt(address.getCreatedAt());
        vo.setUpdatedAt(address.getUpdatedAt());
        return vo;
    }
}
