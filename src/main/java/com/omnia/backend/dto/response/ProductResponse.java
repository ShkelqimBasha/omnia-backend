package com.omnia.backend.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;

    private String name;

    private String imageUrl;

    private String organizationLogoUrl;

    private String description;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private Integer stock;

    private String brand;

    private String category;

    private Long categoryId;

    private Long organizationId;

    private String organizationName;

    private Long createdByUserId;

    private String status;

    /** GENERAL, CLOTHING, SHOES, SKINCARE, CAR or PROPERTY. */
    private String categoryKind;

    private java.util.Map<String, Object> attributes;

    /** Fields of the category with the listing's values and Albanian labels, ready to show. */
    private java.util.List<AttributeView> attributeDetails;

    /** Cars only: CONTACT_ONLY or CONTACT_AND_BUY. */
    private String saleMode;

    /** True when the listing can go into the cart and be paid online. */
    private boolean purchasable;

    /** "Kontakto" / "Kontakto shitësin". */
    private boolean contactEnabled;

    /** "Kërko vizitë". */
    private boolean visitRequestEnabled;

    /** Seller contact, only on listings that offer contact. */
    private String sellerPhone;
    private String sellerEmail;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttributeView {
        private String key;
        private String label;
        private Object value;
        private String displayValue;
    }
}