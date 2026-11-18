package com.pandago.address.controller;

import com.pandago.address.dto.AddressCreateDTO;
import com.pandago.address.dto.AddressUpdateDTO;
import com.pandago.address.service.AddressService;
import com.pandago.address.vo.AddressVO;
import com.pandago.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 收货地址接口（阶段 5，全部需要登录，身份来自 SecurityContext）。 */
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public Result<List<AddressVO>> list() {
        return Result.ok(addressService.listMyAddresses());
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody AddressCreateDTO dto) {
        return Result.ok(addressService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressUpdateDTO dto) {
        addressService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return Result.ok();
    }
}
