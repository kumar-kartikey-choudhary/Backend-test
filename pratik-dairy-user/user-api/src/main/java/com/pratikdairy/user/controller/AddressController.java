package com.pratikdairy.user.controller;

import com.pratikdairy.user.dto.AddressDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// All endpoints here are ownership-scoped to the currently authenticated user - there is no
// admin "list all addresses" endpoint, deliberately: a customer's saved addresses aren't
// something an admin needs to browse, only something order-service/payment-service might read
// via a targeted lookup in a future pass.
@ResponseBody
@FeignClient(name = "PRATIK-DAIRY-USER", contextId = "addressClient", path = "users/addresses", primary = false, url = "${user.url}")
public interface AddressController {

    @PostMapping
    ResponseEntity<AddressDto> create(@RequestBody AddressDto addressDto);

    @GetMapping
    ResponseEntity<List<AddressDto>> findAll();

    @GetMapping(path = "{id}")
    ResponseEntity<AddressDto> find(@PathVariable(name = "id") String id);

    @PatchMapping(path = "{id}")
    ResponseEntity<AddressDto> update(@PathVariable(name = "id") String id, @RequestBody AddressDto addressDto);

    @PatchMapping(path = "{id}/default")
    ResponseEntity<AddressDto> markDefault(@PathVariable(name = "id") String id);

    @DeleteMapping(path = "{id}")
    void delete(@PathVariable(name = "id") String id);
}