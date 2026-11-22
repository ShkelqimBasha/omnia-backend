package com.omnia.backend.controller;

import com.omnia.backend.dto.request.CreateOrderRequest;
import com.omnia.backend.service.impl.PayPalCheckoutService;
import com.omnia.backend.service.impl.PayPalGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PayPalWebReturnControllerTest {

    private static final String ID = "3f2b8c1e-4d5a-4b6c-8d7e-9f0a1b2c3d4e";

    private PayPalCheckoutService service;
    private PayPalGateway gateway;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PayPalCheckoutService.class);
        gateway = mock(PayPalGateway.class);
        when(gateway.environment()).thenReturn(PayPalGateway.SANDBOX);
        mvc = MockMvcBuilders.standaloneSetup(new PayPalCheckoutController(service, gateway)).build();
    }

    @Test
    void webBuyerIsRedirectedToTheWebCheckoutPage() throws Exception {
        when(gateway.webReturnUrl()).thenReturn("https://shop.example.com/pagesa/paypal");
        mvc.perform(get("/api/paypal/return").param("checkout", ID).param("client", "web"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://shop.example.com/pagesa/paypal/" + ID));
    }

    @Test
    void cancelledWebPaymentKeepsTheCancelFlag() throws Exception {
        when(gateway.webReturnUrl()).thenReturn("https://shop.example.com/pagesa/paypal");
        mvc.perform(get("/api/paypal/return").param("checkout", ID).param("client", "web").param("cancelled", "true"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://shop.example.com/pagesa/paypal/" + ID + "?cancelled=true"));
    }

    @Test
    void appBuyerStillGetsTheAppPageWithOptionalWebLink() throws Exception {
        when(gateway.webReturnUrl()).thenReturn("https://shop.example.com/pagesa/paypal");
        mvc.perform(get("/api/paypal/return").param("checkout", ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("omnia://paypal-return?checkout=" + ID)))
                .andExpect(content().string(containsString("https://shop.example.com/pagesa/paypal/" + ID)));
    }

    @Test
    void withoutWebPageConfiguredNothingChanges() throws Exception {
        when(gateway.webReturnUrl()).thenReturn(null);
        mvc.perform(get("/api/paypal/return").param("checkout", ID).param("client", "web"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("omnia://paypal-return")))
                .andExpect(content().string(not(containsString("Vazhdo në web"))));
    }

    @Test
    void invalidCheckoutIdIsNeverRedirected() throws Exception {
        when(gateway.webReturnUrl()).thenReturn("https://shop.example.com/pagesa/paypal");
        mvc.perform(get("/api/paypal/return").param("checkout", "https://evil.example").param("client", "web"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("evil.example"))));
    }

    @Test
    void createPassesTheWebFlagOnlyForClientWeb() throws Exception {
        String body = "{\"shippingName\":\"A\",\"shippingEmail\":\"a@example.test\",\"shippingPhone\":\"1\","
                + "\"shippingAddress\":\"Rr\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
        mvc.perform(post("/api/paypal/checkouts/" + ID).param("client", "web").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).create(eq(ID), any(CreateOrderRequest.class), eq(true));
        mvc.perform(post("/api/paypal/checkouts/" + ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).create(eq(ID), any(CreateOrderRequest.class), eq(false));
    }
}
