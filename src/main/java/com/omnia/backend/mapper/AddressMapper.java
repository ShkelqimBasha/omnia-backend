package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.AddressResponse;
import com.omnia.backend.entity.Address;

public class AddressMapper {

    public static AddressResponse toResponse(Address address) {

        return AddressResponse.builder()
                .id(address.getId())
                .userId(address.getUser().getId())
                .country(address.getCountry())
                .city(address.getCity())
                .street(address.getStreet())
                .zipCode(address.getZipCode())
                .isDefault(address.getIsDefault())
                .build();
    }

    private AddressMapper() {
    }
}