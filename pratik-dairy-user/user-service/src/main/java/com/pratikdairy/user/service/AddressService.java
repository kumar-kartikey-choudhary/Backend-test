package com.pratikdairy.user.service;

import com.pratikdairy.user.dto.AddressDto;

import java.util.List;

public interface AddressService {

    AddressDto create(AddressDto addressDto);

    List<AddressDto> findAll();

    AddressDto find(String id);

    AddressDto update(AddressDto addressDto, String id);

    AddressDto markDefault(String id);

    void delete(String id);
}