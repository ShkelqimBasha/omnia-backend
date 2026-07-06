package com.omnia.backend.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    private String country;

    private String city;

    private String street;

    private String zipCode;

    private Boolean isDefault;
}