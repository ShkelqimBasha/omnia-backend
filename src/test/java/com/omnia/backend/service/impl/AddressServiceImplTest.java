package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.AddressRequest;
import com.omnia.backend.dto.response.AddressResponse;
import com.omnia.backend.entity.Address;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.AddressRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRequest addressRequest;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User currentUser;
    private User anotherUser;
    private Address address;

    @BeforeEach
    void setUp() {

        currentUser = User.builder()
                .id(1L)
                .username("shkelqim123")
                .email("shkelqim@example.com")
                .build();

        anotherUser = User.builder()
                .id(2L)
                .username("anotherUser")
                .email("another@example.com")
                .build();

        address = Address.builder()
                .id(10L)
                .user(currentUser)
                .country("Albania")
                .city("Tirana")
                .street("Rruga Mine Peza")
                .zipCode("1001")
                .isDefault(true)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAddress_shouldCreateAddressSuccessfully() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRequest.getCountry())
                .thenReturn("Albania");

        when(addressRequest.getCity())
                .thenReturn("Tirana");

        when(addressRequest.getStreet())
                .thenReturn("Rruga Mine Peza");

        when(addressRequest.getZipCode())
                .thenReturn("1001");

        when(addressRequest.getIsDefault())
                .thenReturn(true);

        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> {
                    Address savedAddress = invocation.getArgument(0);
                    savedAddress.setId(10L);
                    return savedAddress;
                });

        AddressResponse result =
                addressService.createAddress(addressRequest);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals("Albania", result.getCountry());
        assertEquals("Tirana", result.getCity());
        assertEquals("Rruga Mine Peza", result.getStreet());
        assertEquals("1001", result.getZipCode());
        assertTrue(result.getIsDefault());

        verify(addressRepository)
                .save(any(Address.class));
    }

    @Test
    void createAddress_shouldSetDefaultFalseWhenRequestValueIsNull() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRequest.getCountry())
                .thenReturn("Albania");

        when(addressRequest.getCity())
                .thenReturn("Tirana");

        when(addressRequest.getStreet())
                .thenReturn("Rruga Kavajes");

        when(addressRequest.getZipCode())
                .thenReturn("1001");

        when(addressRequest.getIsDefault())
                .thenReturn(null);

        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> {
                    Address savedAddress = invocation.getArgument(0);
                    savedAddress.setId(11L);
                    return savedAddress;
                });

        AddressResponse result =
                addressService.createAddress(addressRequest);

        assertNotNull(result);
        assertFalse(result.getIsDefault());
    }

    @Test
    void getMyAddresses_shouldReturnCurrentUsersAddresses() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        Address secondAddress = Address.builder()
                .id(11L)
                .user(currentUser)
                .country("Albania")
                .city("Durres")
                .street("Rruga Aleksander Goga")
                .zipCode("2001")
                .isDefault(false)
                .build();

        when(addressRepository.findByUserId(1L))
                .thenReturn(List.of(address, secondAddress));

        List<AddressResponse> result =
                addressService.getMyAddresses();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Tirana", result.get(0).getCity());
        assertEquals("Durres", result.get(1).getCity());

        verify(addressRepository)
                .findByUserId(1L);
    }

    @Test
    void updateAddress_shouldUpdateAddressSuccessfully() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(10L))
                .thenReturn(Optional.of(address));

        when(addressRequest.getCountry())
                .thenReturn("Albania");

        when(addressRequest.getCity())
                .thenReturn("Durres");

        when(addressRequest.getStreet())
                .thenReturn("Rruga e Re");

        when(addressRequest.getZipCode())
                .thenReturn("2001");

        when(addressRequest.getIsDefault())
                .thenReturn(false);

        when(addressRepository.save(address))
                .thenReturn(address);

        AddressResponse result =
                addressService.updateAddress(
                        10L,
                        addressRequest
                );

        assertNotNull(result);
        assertEquals("Durres", result.getCity());
        assertEquals("Rruga e Re", result.getStreet());
        assertEquals("2001", result.getZipCode());
        assertFalse(result.getIsDefault());

        verify(addressRepository).save(address);
    }

    @Test
    void updateAddress_shouldThrowWhenAddressDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> addressService.updateAddress(
                                99L,
                                addressRequest
                        )
                );

        assertEquals(
                "Address not found",
                exception.getMessage()
        );

        verify(addressRepository, never())
                .save(any(Address.class));
    }

    @Test
    void updateAddress_shouldThrowWhenUserIsNotOwner() {

        address.setUser(anotherUser);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(10L))
                .thenReturn(Optional.of(address));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> addressService.updateAddress(
                                10L,
                                addressRequest
                        )
                );

        assertEquals(
                "You are not allowed to update this address",
                exception.getMessage()
        );

        verify(addressRepository, never())
                .save(any(Address.class));
    }

    @Test
    void deleteAddress_shouldDeleteAddressSuccessfully() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(10L))
                .thenReturn(Optional.of(address));

        addressService.deleteAddress(10L);

        verify(addressRepository)
                .delete(address);
    }

    @Test
    void deleteAddress_shouldThrowWhenAddressDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> addressService.deleteAddress(99L)
                );

        assertEquals(
                "Address not found",
                exception.getMessage()
        );

        verify(addressRepository, never())
                .delete(any(Address.class));
    }

    @Test
    void deleteAddress_shouldThrowWhenUserIsNotOwner() {

        address.setUser(anotherUser);

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findById(10L))
                .thenReturn(Optional.of(address));

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> addressService.deleteAddress(10L)
                );

        assertEquals(
                "You are not allowed to delete this address",
                exception.getMessage()
        );

        verify(addressRepository, never())
                .delete(any(Address.class));
    }

    @Test
    void getCurrentUser_shouldFallbackToUsername() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim123",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userRepository.findByEmail("shkelqim123"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim123"))
                .thenReturn(Optional.of(currentUser));

        when(addressRepository.findByUserId(1L))
                .thenReturn(List.of(address));

        List<AddressResponse> result =
                addressService.getMyAddresses();

        assertEquals(1, result.size());

        verify(userRepository)
                .findByUsername("shkelqim123");
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim@example.com"))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> addressService.getMyAddresses()
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verifyNoInteractions(addressRepository);
    }
}