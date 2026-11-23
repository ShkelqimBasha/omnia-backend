package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.ProductResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    private com.omnia.backend.catalog.CatalogSettings catalogSettings;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCatalogSettings(com.omnia.backend.catalog.CatalogSettings catalogSettings) {
        this.catalogSettings = catalogSettings;
    }

    public ProductResponse toResponse(
            Product product
    ) {
        if (product == null) {
            throw new IllegalArgumentException(
                    "Product must not be null"
            );
        }

        Organization organization =
                product.getOrganization();

        User createdBy =
                product.getCreatedBy();

        com.omnia.backend.enums.CategoryKind kind =
                com.omnia.backend.enums.CategoryKind.fromNullable(
                        product.getCategory().getKind());
        boolean contact = kind.allowsContact();
        java.util.List<ProductResponse.SizeView> sizes = sizeViews(kind, product);

        return ProductResponse.builder()
                .categoryKind(kind.name())
                .attributes(product.getAttributes())
                .attributeDetails(attributeDetails(kind, product))
                .saleMode(product.getSaleMode() == null ? null : product.getSaleMode().name())
                .purchasable(com.omnia.backend.catalog.ListingRules.isPurchasable(
                        kind, product.getSaleMode(),
                        com.omnia.backend.catalog.CatalogSettings.carOnline(catalogSettings)))
                .contactEnabled(contact)
                .visitRequestEnabled(kind.allowsVisitRequest())
                .sellerPhone(contact && organization != null ? organization.getPhone() : null)
                .sellerEmail(contact && organization != null ? organization.getBusinessEmail() : null)
                .id(product.getId())
                .name(product.getName())
                .imageUrl(primaryImageUrl(product))
                .imageUrls(galleryImageUrls(product))
                .organizationLogoUrl(organization == null || organization.getLogoFile() == null
                        ? null : "/api/files/" + organization.getLogoFile().getId())
                .description(product.getDescription())
                .price(product.getPrice())
                .discountPrice(
                        product.getDiscountPrice()
                )
                .stock(product.getStock())
                .brand(product.getBrand())
                .category(
                        product.getCategory().getName()
                )
                .categoryId(
                        product.getCategory().getId()
                )
                .organizationId(
                        organization == null
                                ? null
                                : organization.getId()
                )
                .organizationName(
                        organization == null
                                ? null
                                : organization.getName()
                )
                .createdByUserId(
                        createdBy == null
                                ? null
                                : createdBy.getId()
                )
                .status(
                        product.getStatus().name()
                )
                .sizes(sizes)
                .sizeRequired(!sizes.isEmpty())
                .build();
    }

    /** Sizes of clothing and shoes; [] for other kinds and for listings without sizes. */
    static java.util.List<ProductResponse.SizeView> sizeViews(
            com.omnia.backend.enums.CategoryKind kind, Product product) {
        return com.omnia.backend.catalog.ProductSizes.sizesOf(product).stream()
                .map(v -> ProductResponse.SizeView.builder()
                        .id(v.getId())
                        .value(v.getVariantValue())
                        .label(com.omnia.backend.catalog.ProductSizes.label(v.getVariantValue()))
                        .stock(v.getStock() == null ? 0 : v.getStock())
                        .build())
                .toList();
    }
    private static java.util.List<ProductResponse.AttributeView> attributeDetails(
            com.omnia.backend.enums.CategoryKind kind, Product product) {
        java.util.List<ProductResponse.AttributeView> views = new java.util.ArrayList<>();
        java.util.Map<String, Object> values = product.getAttributes() == null
                ? java.util.Map.of() : product.getAttributes();
        for (com.omnia.backend.catalog.CategoryField field
                : com.omnia.backend.catalog.CategoryFieldCatalog.fieldsFor(kind)) {
            Object value = field.storedAsBrand() ? product.getBrand() : values.get(field.key());
            if (value == null || String.valueOf(value).isBlank()) continue;
            String display = field.type() == com.omnia.backend.catalog.CategoryField.FieldType.SELECT
                    ? field.optionLabel(String.valueOf(value))
                    : number(value, field.unit() != null) + (field.unit() == null ? "" : " " + field.unit());
            views.add(ProductResponse.AttributeView.builder()
                    .key(field.key()).label(field.label()).value(value).displayValue(display).build());
        }
        return views;
    }

    /** "145000" -> "145 000" for measured values (km, m2, ml); years stay "2017". */
    static String number(Object value, boolean group) {
        String text = String.valueOf(value);
        if (!group || !text.matches("[0-9]{4,}")) return text;
        StringBuilder out = new StringBuilder(text);
        for (int i = out.length() - 3; i > 0; i -= 3) out.insert(i, ' ');
        return out.toString();
    }

    public static String primaryImageUrl(Product product) {
        java.util.List<String> urls = galleryImageUrls(product);
        return urls.isEmpty() ? null : urls.get(0);
    }

    /** Every photo of the listing in gallery order: the cover first, then by position. */
    public static java.util.List<String> galleryImageUrls(Product product) {
        if (product == null || product.getImages() == null) return java.util.List.of();
        return product.getImages().stream()
                .filter(image -> image.getUploadedFile() != null || image.isLegacyUrlBacked())
                .sorted(GALLERY_ORDER)
                .map(image -> ProductImageMapper.toResponse(image).getImageUrl())
                .toList();
    }

    public static final java.util.Comparator<com.omnia.backend.entity.ProductImage> GALLERY_ORDER =
            java.util.Comparator
                    .comparing((com.omnia.backend.entity.ProductImage image) ->
                            !Boolean.TRUE.equals(image.getIsPrimary()))
                    .thenComparing(image -> image.getSortOrder() == null ? 0 : image.getSortOrder())
                    .thenComparing(com.omnia.backend.entity.ProductImage::getId,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
}
