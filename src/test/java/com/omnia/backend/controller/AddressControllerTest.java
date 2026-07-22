package com.omnia.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.dto.request.AddressRequest;
import com.omnia.backend.dto.response.AddressResponse;
import com.omnia.backend.service.interfaces.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    private static final Long ADDRESS_ID = 10L;
    private static final Long USER_ID = 20L;

    @Mock
    private AddressService addressService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        AddressController controller =
                new AddressController(addressService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
    }

    @Test
    void createAddress_WithValidRequest_ShouldReturnCreated()
            throws Exception {

        AddressRequest request =
                createRequest(
                        "Albania",
                        "Tirana",
                        "Rruga e Durresit",
                        "1001",
                        true
                );

        AddressResponse response =
                createResponse(
                        ADDRESS_ID,
                        "Albania",
                        "Tirana",
                        "Rruga e Durresit",
                        "1001",
                        true
                );

        when(
                addressService.createAddress(
                        any(AddressRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post("/api/addresses")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(ADDRESS_ID)
                )
                .andExpect(
                        jsonPath("$.userId")
                                .value(USER_ID)
                )
                .andExpect(
                        jsonPath("$.country")
                                .value("Albania")
                )
                .andExpect(
                        jsonPath("$.city")
                                .value("Tirana")
                )
                .andExpect(
                        jsonPath("$.street")
                                .value("Rruga e Durresit")
                )
                .andExpect(
                        jsonPath("$.zipCode")
                                .value("1001")
                )
                .andExpect(
                        jsonPath("$.isDefault")
                                .value(true)
                );

        ArgumentCaptor<AddressRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        AddressRequest.class
                );

        verify(addressService).createAddress(
                requestCaptor.capture()
        );

        AddressRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getCountry())
                .isEqualTo("Albania");

        assertThat(capturedRequest.getCity())
                .isEqualTo("Tirana");

        assertThat(capturedRequest.getStreet())
                .isEqualTo("Rruga e Durresit");

        assertThat(capturedRequest.getZipCode())
                .isEqualTo("1001");

        assertThat(capturedRequest.getIsDefault())
                .isTrue();

        verifyNoMoreInteractions(addressService);
    }

    @Test
    void getMyAddresses_ShouldReturnAddresses()
            throws Exception {

        AddressResponse firstAddress =
                createResponse(
                        10L,
                        "Albania",
                        "Tirana",
                        "Rruga e Durresit",
                        "1001",
                        true
                );

        AddressResponse secondAddress =
                createResponse(
                        11L,
                        "Albania",
                        "Durres",
                        "Rruga Aleksander Goga",
                        "2001",
                        false
                );

        when(addressService.getMyAddresses())
                .thenReturn(
                        List.of(
                                firstAddress,
                                secondAddress
                        )
                );

        mockMvc.perform(
                        get("/api/addresses")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(
                        jsonPath("$.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$[0].id")
                                .value(10L)
                )
                .andExpect(
                        jsonPath("$[0].userId")
                                .value(USER_ID)
                )
                .andExpect(
                        jsonPath("$[0].city")
                                .value("Tirana")
                )
                .andExpect(
                        jsonPath("$[0].isDefault")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(11L)
                )
                .andExpect(
                        jsonPath("$[1].city")
                                .value("Durres")
                )
                .andExpect(
                        jsonPath("$[1].street")
                                .value("Rruga Aleksander Goga")
                )
                .andExpect(
                        jsonPath("$[1].isDefault")
                                .value(false)
                );

        verify(addressService).getMyAddresses();
        verifyNoMoreInteractions(addressService);
    }

    @Test
    void updateAddress_WithValidRequest_ShouldReturnUpdatedAddress()
            throws Exception {

        AddressRequest request =
                createRequest(
                        "Albania",
                        "Shkoder",
                        "Rruga Kol Idromeno",
                        "4001",
                        false
                );

        AddressResponse response =
                createResponse(
                        ADDRESS_ID,
                        "Albania",
                        "Shkoder",
                        "Rruga Kol Idromeno",
                        "4001",
                        false
                );

        when(
                addressService.updateAddress(
                        eq(ADDRESS_ID),
                        any(AddressRequest.class)
                )
        ).thenReturn(response);

        mockMvc.perform(
                        put(
                                "/api/addresses/{id}",
                                ADDRESS_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.id")
                                .value(ADDRESS_ID)
                )
                .andExpect(
                        jsonPath("$.country")
                                .value("Albania")
                )
                .andExpect(
                        jsonPath("$.city")
                                .value("Shkoder")
                )
                .andExpect(
                        jsonPath("$.street")
                                .value("Rruga Kol Idromeno")
                )
                .andExpect(
                        jsonPath("$.zipCode")
                                .value("4001")
                )
                .andExpect(
                        jsonPath("$.isDefault")
                                .value(false)
                );

        ArgumentCaptor<AddressRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        AddressRequest.class
                );

        verify(addressService).updateAddress(
                eq(ADDRESS_ID),
                requestCaptor.capture()
        );

        AddressRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest.getCity())
                .isEqualTo("Shkoder");

        assertThat(capturedRequest.getStreet())
                .isEqualTo("Rruga Kol Idromeno");

        assertThat(capturedRequest.getZipCode())
                .isEqualTo("4001");

        assertThat(capturedRequest.getIsDefault())
                .isFalse();

        verifyNoMoreInteractions(addressService);
    }

    @Test
    void deleteAddress_ShouldReturnNoContent()
            throws Exception {

        mockMvc.perform(
                        delete(
                                "/api/addresses/{id}",
                                ADDRESS_ID
                        )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(addressService)
                .deleteAddress(ADDRESS_ID);

        verifyNoMoreInteractions(addressService);
    }

    private AddressRequest createRequest(
            String country,
            String city,
            String street,
            String zipCode,
            Boolean isDefault
    ) {

        return AddressRequest.builder()
                .country(country)
                .city(city)
                .street(street)
                .zipCode(zipCode)
                .isDefault(isDefault)
                .build();
    }

    private AddressResponse createResponse(
            Long id,
            String country,
            String city,
            String street,
            String zipCode,
            Boolean isDefault
    ) {

        return AddressResponse.builder()
                .id(id)
                .userId(USER_ID)
                .country(country)
                .city(city)
                .street(street)
                .zipCode(zipCode)
                .isDefault(isDefault)
                .build();
    }
}