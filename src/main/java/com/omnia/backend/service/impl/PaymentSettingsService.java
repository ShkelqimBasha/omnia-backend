package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.CreateOrderItemRequest;
import com.omnia.backend.dto.request.PaymentOptionsRequest;
import com.omnia.backend.dto.request.PaymentSettingsRequest;
import com.omnia.backend.dto.response.PaymentOptionsResponse;
import com.omnia.backend.dto.response.PaymentSettingsResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.Product;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Payment methods each company accepts, and what that means for a buyer's cart. */
@Service
public class PaymentSettingsService {

    private final OrganizationRepository organizations;
    private final ProductRepository products;
    private final OrganizationAccessService access;

    public PaymentSettingsService(OrganizationRepository organizations, ProductRepository products,
                                  OrganizationAccessService access) {
        this.organizations = organizations;
        this.products = products;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PaymentSettingsResponse get(Long organizationId) {
        access.requireCanAccessOrganization(organizationId);
        return PaymentSettingsResponse.of(require(organizationId));
    }

    /** Company owners/admins (and platform admins) only. */
    @Transactional
    public PaymentSettingsResponse update(Long organizationId, PaymentSettingsRequest request) {
        access.requireCanManageOrders(organizationId);
        if (!request.acceptsCashOnDelivery() && !request.acceptsPaypal())
            throw new IllegalArgumentException("Zgjidh të paktën një mënyrë pagese.");
        BigDecimal min = amount(request.codMinOrderAmount());
        BigDecimal max = amount(request.codMaxOrderAmount());
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new IllegalArgumentException(
                    "Shuma minimale për pagesë në dorëzim nuk mund të jetë mbi shumën maksimale.");
        Organization organization = require(organizationId);
        organization.setAcceptsCashOnDelivery(request.acceptsCashOnDelivery());
        organization.setAcceptsPaypal(request.acceptsPaypal());
        organization.setCodMinOrderAmount(min);
        organization.setCodMaxOrderAmount(max);
        return PaymentSettingsResponse.of(organizations.saveAndFlush(organization));
    }

    /** Read-only: prices as checkout uses them (discount price first), grouped per company. */
    @Transactional(readOnly = true)
    public PaymentOptionsResponse options(PaymentOptionsRequest request) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderItemRequest item : request.items())
            quantities.merge(item.getProductId(), item.getQuantity(), Math::addExact);
        Map<Long, Organization> companies = new HashMap<>();
        Map<Long, BigDecimal> subtotals = new HashMap<>();
        for (Product product : products.findAllById(quantities.keySet())) {
            Organization organization = product.getOrganization();
            if (organization == null) continue;
            BigDecimal price = com.omnia.backend.catalog.Pricing.buyerPrice(product);
            if (price == null) continue;
            companies.putIfAbsent(organization.getId(), organization);
            subtotals.merge(organization.getId(),
                    price.multiply(BigDecimal.valueOf(quantities.get(product.getId()))), BigDecimal::add);
        }
        List<CheckoutPaymentRules.Part> parts = companies.values().stream()
                .sorted(Comparator.comparing(Organization::getId))
                .map(org -> new CheckoutPaymentRules.Part(org, subtotals.get(org.getId())))
                .toList();
        CheckoutPaymentRules.Result result = CheckoutPaymentRules.evaluate(parts);
        return new PaymentOptionsResponse(
                method(result, PaymentMethod.CASH_ON_DELIVERY),
                method(result, PaymentMethod.PAYPAL),
                result.mixedRules(),
                result.message(),
                result.companies().stream().map(company -> {
                    Organization org = company.organization();
                    return new PaymentOptionsResponse.Company(org.getId(), org.getName(), company.subtotal(),
                            org.isAcceptsCashOnDelivery(), org.isAcceptsPaypal(),
                            org.getCodMinOrderAmount(), org.getCodMaxOrderAmount(),
                            company.cashOnDeliveryAvailable(), company.paypalAvailable());
                }).toList());
    }

    private static PaymentOptionsResponse.Method method(CheckoutPaymentRules.Result result, PaymentMethod method) {
        String reason = result.reason(method);
        return new PaymentOptionsResponse.Method(reason == null, reason);
    }

    private Organization require(Long organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
