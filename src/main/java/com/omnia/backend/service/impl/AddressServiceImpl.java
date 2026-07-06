package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.AddressRequest;
import com.omnia.backend.dto.response.AddressResponse;
import com.omnia.backend.entity.Address;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.AddressMapper;
import com.omnia.backend.repository.AddressRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.AddressService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressServiceImpl(
            AddressRepository addressRepository,
            UserRepository userRepository
    ) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public AddressResponse createAddress(AddressRequest request) {

        User user = getCurrentUser();

        Address address = Address.builder()
                .user(user)
                .country(request.getCountry())
                .city(request.getCity())
                .street(request.getStreet())
                .zipCode(request.getZipCode())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .build();

        Address saved = addressRepository.save(address);

        return AddressMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {

        User user = getCurrentUser();

        return addressRepository.findByUserId(user.getId())
                .stream()
                .map(AddressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(Long id, AddressRequest request) {

        User user = getCurrentUser();

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to update this address");
        }

        address.setCountry(request.getCountry());
        address.setCity(request.getCity());
        address.setStreet(request.getStreet());
        address.setZipCode(request.getZipCode());
        address.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));

        Address updated = addressRepository.save(address);

        return AddressMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteAddress(Long id) {

        User user = getCurrentUser();

        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to delete this address");
        }

        addressRepository.delete(address);
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}