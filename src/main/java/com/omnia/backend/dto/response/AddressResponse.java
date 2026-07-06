package com.omnia.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

    private Long id;

    private Long userId;

    private String country;

    private String city;

    private String street;

    private String zipCode;

    private Boolean isDefault;
}