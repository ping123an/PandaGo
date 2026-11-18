package com.pandago.address.service;

import com.pandago.address.dto.AddressCreateDTO;
import com.pandago.address.dto.AddressUpdateDTO;
import com.pandago.address.vo.AddressVO;

import java.util.List;

/** 当前登录用户的收货地址业务；所有操作强制按登录用户隔离。 */
public interface AddressService {

    List<AddressVO> listMyAddresses();

    Long create(AddressCreateDTO dto);

    void update(Long id, AddressUpdateDTO dto);

    void delete(Long id);

    void setDefault(Long id);
}
