package com.pratikdairy.user.controller.impl;

import com.pratikdairy.user.controller.AddressController;
import com.pratikdairy.user.dto.AddressDto;
import com.pratikdairy.user.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Primary
@RequestMapping("users/addresses")
public class AddressControllerImpl implements AddressController {

    private final AddressService addressService;

    @Autowired
    public AddressControllerImpl(AddressService addressService) {
        this.addressService = addressService;
    }

    @Override
    public ResponseEntity<AddressDto> create(AddressDto addressDto) {
        return new ResponseEntity<>(addressService.create(addressDto), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<List<AddressDto>> findAll() {
        return ResponseEntity.ok(addressService.findAll());
    }

    @Override
    public ResponseEntity<AddressDto> find(String id) {
        return ResponseEntity.ok(addressService.find(id));
    }

    @Override
    public ResponseEntity<AddressDto> update(String id, AddressDto addressDto) {
        return ResponseEntity.ok(addressService.update(addressDto, id));
    }

    @Override
    public ResponseEntity<AddressDto> markDefault(String id) {
        return ResponseEntity.ok(addressService.markDefault(id));
    }

    @Override
    public void delete(String id) {
        addressService.delete(id);
    }
}