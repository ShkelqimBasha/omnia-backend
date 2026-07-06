package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.AddressRequest;
import com.omnia.backend.dto.response.AddressResponse;

import java.util.List;

public interface AddressService {

    AddressResponse createAddress(AddressRequest request);

    List<AddressResponse> getMyAddresses();

    AddressResponse updateAddress(Long id, AddressRequest request);

    void deleteAddress(Long id);
}