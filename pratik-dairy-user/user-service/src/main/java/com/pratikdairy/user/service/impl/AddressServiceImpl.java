package com.pratikdairy.user.service.impl;

import com.pratikdairy.user.dto.AddressDto;
import com.pratikdairy.user.model.Address;
import com.pratikdairy.user.model.User;
import com.pratikdairy.user.repository.AddressRepository;
import com.pratikdairy.user.repository.UserRepository;
import com.pratikdairy.user.service.AddressService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Autowired
    public AddressServiceImpl(AddressRepository addressRepository, UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    private String getUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return auth.getName();
    }

    private User getCurrentUser() {
        return userRepository.findByUsername(getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    @Override
    @Transactional
    public AddressDto create(AddressDto addressDto) {
        log.info("Inside @class AddressServiceImpl @method create @param addressDto: {}", addressDto);
        User user = getCurrentUser();

        Address address = new Address();
        address.setUser(user);
        address.setLabel(addressDto.getLabel());
        address.setLine1(addressDto.getLine1());
        address.setLine2(addressDto.getLine2());
        address.setCity(addressDto.getCity());
        address.setState(addressDto.getState());
        address.setPincode(addressDto.getPincode());
        address.setLandmark(addressDto.getLandmark());
        address.setLatitude(addressDto.getLatitude());
        address.setLongitude(addressDto.getLongitude());

        // First address for this user is automatically the default - there should never be a
        // moment where a customer has addresses saved but none marked default.
        boolean isFirst = addressRepository.findByUser_Username(user.getUsername()).isEmpty();
        address.setDefault(isFirst || addressDto.isDefault());
        if (address.isDefault() && !isFirst) {
            unsetExistingDefault(user.getUsername());
        }

        return toDto(addressRepository.saveAndFlush(address));
    }

    @Override
    public List<AddressDto> findAll() {
        return addressRepository.findByUser_Username(getUsername()).stream()
                .map(this::toDto).toList();
    }

    @Override
    public AddressDto find(String id) {
        return toDto(getOwnedOrThrow(id));
    }

    @Override
    @Transactional
    public AddressDto update(AddressDto addressDto, String id) {
        Address address = getOwnedOrThrow(id);

        if (addressDto.getLabel() != null) address.setLabel(addressDto.getLabel());
        if (addressDto.getLine1() != null) address.setLine1(addressDto.getLine1());
        address.setLine2(addressDto.getLine2());
        if (addressDto.getCity() != null) address.setCity(addressDto.getCity());
        if (addressDto.getState() != null) address.setState(addressDto.getState());
        if (addressDto.getPincode() != null) address.setPincode(addressDto.getPincode());
        address.setLandmark(addressDto.getLandmark());
        address.setLatitude(addressDto.getLatitude());
        address.setLongitude(addressDto.getLongitude());

        return toDto(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public AddressDto markDefault(String id) {
        Address address = getOwnedOrThrow(id);
        unsetExistingDefault(address.getUser().getUsername());
        address.setDefault(true);
        return toDto(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void delete(String id) {
        Address address = getOwnedOrThrow(id);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);

        // If the default address just got deleted, promote another one (if any) so the
        // customer isn't left with saved addresses but none marked default.
        if (wasDefault) {
            addressRepository.findByUser_Username(address.getUser().getUsername()).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.saveAndFlush(next);
                    });
        }
    }

    private void unsetExistingDefault(String username) {
        addressRepository.findByUser_Username(username).stream()
                .filter(Address::isDefault)
                .forEach(a -> {
                    a.setDefault(false);
                    addressRepository.saveAndFlush(a);
                });
    }

    private Address getOwnedOrThrow(String id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Address not found with id: " + id));
        if (!address.getUser().getUsername().equals(getUsername())) {
            throw new AccessDeniedException("This address does not belong to the current user");
        }
        return address;
    }

    private AddressDto toDto(Address address) {
        AddressDto dto = new AddressDto();
        dto.setId(address.getId());
        dto.setLabel(address.getLabel());
        dto.setLine1(address.getLine1());
        dto.setLine2(address.getLine2());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPincode(address.getPincode());
        dto.setLandmark(address.getLandmark());
        dto.setLatitude(address.getLatitude());
        dto.setLongitude(address.getLongitude());
        dto.setDefault(address.isDefault());
        dto.setCreatedAt(address.getCreatedAt());
        dto.setModifiedAt(address.getModifiedAt());
        return dto;
    }
}