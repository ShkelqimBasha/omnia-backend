package com.omnia.backend.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;

    private Long parentId;

    private String parentName;

    private String name;

    private String description;

    private String imageUrl;

    private String status;

    /** GENERAL, CLOTHING, SHOES, SKINCARE, CAR or PROPERTY. */
    private String kind;

    private Integer sortOrder;

    /** Listing form fields and filters of this category. */
    private java.util.List<com.omnia.backend.catalog.CategoryField> fields;

    /** Cart and online payment. */
    private boolean cartEnabled;

    /** "Kontakto shitësin" / "Kontakto". */
    private boolean contactEnabled;

    /** "Kërko vizitë". */
    private boolean visitRequestEnabled;

    /** Cars: the seller may offer "Kontakt + blerje online". */
    private boolean onlinePurchaseOption;
}