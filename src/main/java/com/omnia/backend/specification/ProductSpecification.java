package com.omnia.backend.specification;

import com.omnia.backend.entity.Product;
import com.omnia.backend.catalog.CatalogFilters;
import com.omnia.backend.catalog.CatalogSort;
import com.omnia.backend.catalog.ProductSizes;
import com.omnia.backend.entity.OrderItem;
import com.omnia.backend.entity.Payment;
import com.omnia.backend.entity.ProductDailyStat;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.entity.Review;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.enums.PaymentStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.enums.SaleMode;
import com.omnia.backend.service.impl.DeliveryPricing;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecification {

    private static final char LIKE_ESCAPE_CHARACTER = '\\';

    private ProductSpecification() {
    }

    public static Specification<Product> filterProducts(
            String keyword,
            Long categoryId,
            Long organizationId,
            String brand,
            ProductStatus status,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return Specification.allOf(
                List.of(
                        containsKeyword(keyword),
                        hasCategory(categoryId),
                        hasOrganization(organizationId),
                        hasBrand(brand),
                        hasStatus(status),
                        priceGreaterThanOrEqual(minPrice),
                        priceLessThanOrEqual(maxPrice)
                )
        );
    }

    /** Filters on category-specific listing fields stored in {@code products.attributes}. */
    public static Specification<Product> hasAttributes(
            List<com.omnia.backend.catalog.AttributeFilter> filters
    ) {
        return (root, query, cb) -> {
            if (filters == null || filters.isEmpty()) {
                return cb.conjunction();
            }
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            for (com.omnia.backend.catalog.AttributeFilter filter : filters) {
                jakarta.persistence.criteria.Expression<String> path = cb.literal(filter.jsonPath());
                switch (filter.operator()) {
                    case MIN, MAX, EQUALS -> {
                        if (filter.number() != null) {
                            jakarta.persistence.criteria.Expression<BigDecimal> number = cb.function(
                                    "json_extract", BigDecimal.class, root.get("attributes"), path);
                            predicates.add(switch (filter.operator()) {
                                case MIN -> cb.greaterThanOrEqualTo(number, filter.number());
                                case MAX -> cb.lessThanOrEqualTo(number, filter.number());
                                default -> cb.equal(number, filter.number());
                            });
                        } else {
                            predicates.add(cb.equal(text(cb, root, path), filter.text()));
                        }
                    }
                    case CONTAINS -> predicates.add(cb.like(
                            cb.lower(text(cb, root, path)),
                            "%" + escapeLikePattern(filter.text()) + "%",
                            LIKE_ESCAPE_CHARACTER));
                }
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    /**
     * Products that have at least one of these sizes in stock ("SIZE" variants with stock > 0).
     * Values come from {@link com.omnia.backend.catalog.ProductSizes#parseFilter}.
     */
    public static Specification<Product> hasAnySize(List<String> sizes) {
        return (root, query, cb) -> {
            if (sizes == null || sizes.isEmpty()) {
                return cb.conjunction();
            }
            jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
            jakarta.persistence.criteria.Root<com.omnia.backend.entity.ProductVariant> variant =
                    sub.from(com.omnia.backend.entity.ProductVariant.class);
            sub.select(variant.get("id")).where(
                    cb.equal(variant.get("product"), root),
                    cb.equal(variant.get("variantName"), com.omnia.backend.catalog.ProductSizes.VARIANT_NAME),
                    variant.get("variantValue").in(sizes),
                    cb.greaterThan(variant.get("stock"), 0));
            return cb.exists(sub);
        };
    }

    /** Kinds that go into the cart and ship (see {@link CategoryKind#usesCart()}). */
    private static final List<CategoryKind> CART_KINDS = java.util.Arrays.stream(CategoryKind.values())
            .filter(CategoryKind::usesCart).toList();

    /** Kinds handled by contacting the seller (cars, property). */
    private static final List<CategoryKind> CONTACT_KINDS = java.util.Arrays.stream(CategoryKind.values())
            .filter(CategoryKind::allowsContact).toList();

    /** Days of views counted by the "popular" sort (today included). */
    public static final int POPULAR_WINDOW_DAYS = 30;

    /**
     * General catalog filters ({@link CatalogFilters}).
     *
     * @param carOnlinePurchaseEnabled whether "Kontakt + blerje online" cars can be bought online
     * @param now                      current time, for {@code publishedWithinDays}
     */
    public static Specification<Product> catalogFilters(
            CatalogFilters filters,
            boolean carOnlinePurchaseEnabled,
            LocalDateTime now
    ) {
        return (root, query, cb) -> {
            if (filters == null || filters.isEmpty()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (filters.inStock()) {
                predicates.add(inStock(root, query, cb));
            }
            if (filters.onSale()) {
                predicates.add(cb.and(
                        cb.isNotNull(root.get("discountPrice")),
                        cb.greaterThan(root.<BigDecimal>get("discountPrice"), BigDecimal.ZERO),
                        cb.lessThan(root.get("discountPrice"), root.<BigDecimal>get("price"))));
            }
            if (filters.verifiedSeller()) {
                predicates.add(cb.isNotNull(root.get("organization").get("verifiedAt")));
            }
            if (filters.sellerType() != null) {
                predicates.add(cb.equal(root.get("organization").get("sellerType"), filters.sellerType()));
            }
            if (filters.minRating() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        averageRating(root, query, cb), filters.minRating().doubleValue()));
            }
            if (filters.freeShipping()) {
                Expression<BigDecimal> effectivePrice =
                        cb.coalesce(root.<BigDecimal>get("discountPrice"), root.<BigDecimal>get("price"));
                predicates.add(cb.and(
                        purchasableOnline(root, cb, carOnlinePurchaseEnabled),
                        cb.greaterThan(effectivePrice, DeliveryPricing.FREE_SHIPPING_THRESHOLD)));
            }
            if (filters.publishedWithinDays() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get("createdAt"),
                        now.minusDays(filters.publishedWithinDays())));
            }
            if (filters.paymentOnline()) {
                predicates.add(cb.and(
                        cb.isTrue(root.get("organization").get("acceptsPaypal")),
                        purchasableOnline(root, cb, carOnlinePurchaseEnabled)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Sorts that need data outside the product row. They are added with {@code query.orderBy},
     * so the Pageable must be unsorted; count queries are left alone. Every sort ends with
     * {@code id desc} so pages are stable.
     *
     * @param today the "popular" window is {@code today - 29 .. today}
     */
    public static Specification<Product> sortedBy(
            CatalogSort sort,
            String keyword,
            LocalDate today
    ) {
        return (root, query, cb) -> {
            if (sort == null || isCountQuery(query)) {
                return cb.conjunction();
            }
            List<Order> orders = new ArrayList<>();
            switch (sort) {
                case RATING -> {
                    orders.add(cb.asc(cb.<Integer>selectCase()
                            .when(cb.isNull(averageRating(root, query, cb)), 1)
                            .otherwise(0)));
                    orders.add(cb.desc(averageRating(root, query, cb)));
                    orders.add(cb.desc(reviewCount(root, query, cb)));
                }
                case POPULAR -> {
                    Subquery<Long> views = query.subquery(Long.class);
                    Root<ProductDailyStat> stat = views.from(ProductDailyStat.class);
                    views.select(cb.coalesce(cb.sumAsLong(stat.get("views")), 0L)).where(
                            cb.equal(stat.get("id").get("productId"), root.get("id")),
                            cb.greaterThanOrEqualTo(stat.get("id").<LocalDate>get("statDate"),
                                    today.minusDays(POPULAR_WINDOW_DAYS - 1L)));
                    orders.add(cb.desc(views));
                }
                case BESTSELLING -> orders.add(cb.desc(quantitySold(root, query, cb)));
                case RELEVANCE -> {
                    String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
                    if (!normalized.isEmpty()) {
                        String escaped = escapeLikePattern(normalized);
                        Expression<String> name = cb.lower(root.get("name"));
                        Expression<String> brand = cb.lower(root.get("brand"));
                        orders.add(cb.asc(cb.<Integer>selectCase()
                                .when(cb.equal(name, normalized), 0)
                                .when(cb.like(name, escaped + "%", LIKE_ESCAPE_CHARACTER), 1)
                                .when(cb.like(name, "%" + escaped + "%", LIKE_ESCAPE_CHARACTER), 2)
                                .when(cb.like(brand, "%" + escaped + "%", LIKE_ESCAPE_CHARACTER), 3)
                                .otherwise(4)));
                    }
                    orders.add(cb.desc(root.get("createdAt")));
                }
            }
            orders.add(cb.desc(root.get("id")));
            query.orderBy(orders);
            return cb.conjunction();
        };
    }

    private static boolean isCountQuery(CriteriaQuery<?> query) {
        Class<?> type = query.getResultType();
        return type == Long.class || type == long.class;
    }

    /**
     * Units sold: items of DELIVERED orders, or of orders that are not cancelled and have a
     * successful payment.
     */
    private static Subquery<Long> quantitySold(Root<Product> root, CriteriaQuery<?> query,
                                               CriteriaBuilder cb) {
        Subquery<Long> sold = query.subquery(Long.class);
        Root<OrderItem> item = sold.from(OrderItem.class);
        Join<OrderItem, com.omnia.backend.entity.Order> order = item.join("order");
        Subquery<Long> payment = sold.subquery(Long.class);
        Root<Payment> paid = payment.from(Payment.class);
        payment.select(paid.get("id")).where(
                cb.equal(paid.get("order"), order),
                cb.equal(paid.get("status"), PaymentStatus.SUCCESS));
        sold.select(cb.coalesce(cb.sumAsLong(item.get("quantity")), 0L)).where(
                cb.equal(item.get("productId"), root.get("id")),
                cb.or(
                        cb.equal(order.get("status"), OrderStatus.DELIVERED),
                        cb.and(
                                cb.notEqual(order.get("status"), OrderStatus.CANCELLED),
                                cb.exists(payment))));
        return sold;
    }

    private static Predicate inStock(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Path<CategoryKind> kind = root.get("category").get("kind");

        Subquery<Long> anySize = query.subquery(Long.class);
        Root<ProductVariant> size = anySize.from(ProductVariant.class);
        anySize.select(size.get("id")).where(
                cb.equal(size.get("product"), root),
                cb.equal(size.get("variantName"), ProductSizes.VARIANT_NAME));

        Subquery<Long> sizeInStock = query.subquery(Long.class);
        Root<ProductVariant> stocked = sizeInStock.from(ProductVariant.class);
        sizeInStock.select(stocked.get("id")).where(
                cb.equal(stocked.get("product"), root),
                cb.equal(stocked.get("variantName"), ProductSizes.VARIANT_NAME),
                cb.greaterThan(stocked.get("stock"), 0));

        Predicate contactListing = cb.and(
                kind.in(CONTACT_KINDS),
                cb.equal(root.get("status"), ProductStatus.ACTIVE));
        Predicate shippedListing = cb.and(
                cb.or(cb.isNull(kind), kind.in(CART_KINDS)),
                cb.notEqual(root.get("status"), ProductStatus.OUT_OF_STOCK),
                cb.or(
                        cb.and(cb.not(cb.exists(anySize)), cb.greaterThan(root.get("stock"), 0)),
                        cb.exists(sizeInStock)));
        return cb.or(contactListing, shippedListing);
    }

    /** Listings that can be put in the cart and paid online (see ListingRules.isPurchasable). */
    private static Predicate purchasableOnline(Root<Product> root, CriteriaBuilder cb,
                                               boolean carOnlinePurchaseEnabled) {
        Path<CategoryKind> kind = root.get("category").get("kind");
        Predicate cart = cb.or(cb.isNull(kind), kind.in(CART_KINDS));
        if (!carOnlinePurchaseEnabled) {
            return cart;
        }
        return cb.or(cart, cb.and(
                cb.equal(kind, CategoryKind.CAR),
                cb.equal(root.get("saleMode"), SaleMode.CONTACT_AND_BUY)));
    }

    private static Subquery<Double> averageRating(Root<Product> root, CriteriaQuery<?> query,
                                                  CriteriaBuilder cb) {
        Subquery<Double> sub = query.subquery(Double.class);
        Root<Review> review = sub.from(Review.class);
        sub.select(cb.avg(review.get("rating"))).where(
                cb.equal(review.get("product"), root),
                cb.isFalse(review.get("hidden")));
        return sub;
    }

    private static Subquery<Long> reviewCount(Root<Product> root, CriteriaQuery<?> query,
                                              CriteriaBuilder cb) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Review> review = sub.from(Review.class);
        sub.select(cb.count(review)).where(
                cb.equal(review.get("product"), root),
                cb.isFalse(review.get("hidden")));
        return sub;
    }

    private static jakarta.persistence.criteria.Expression<String> text(
            jakarta.persistence.criteria.CriteriaBuilder cb,
            jakarta.persistence.criteria.Root<Product> root,
            jakarta.persistence.criteria.Expression<String> path
    ) {
        return cb.function("json_unquote", String.class,
                cb.function("json_extract", String.class, root.get("attributes"), path));
    }

    private static Specification<Product> containsKeyword(
            String keyword
    ) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedKeyword = keyword
                    .trim()
                    .toLowerCase(Locale.ROOT);

            String likePattern = "%"
                    + escapeLikePattern(normalizedKeyword)
                    + "%";

            return criteriaBuilder.or(
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("name")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("description")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    ),
                    criteriaBuilder.like(
                            criteriaBuilder.lower(root.get("brand")),
                            likePattern,
                            LIKE_ESCAPE_CHARACTER
                    )
            );
        };
    }

    private static Specification<Product> hasCategory(
            Long categoryId
    ) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("category").get("id"),
                    categoryId
            );
        };
    }

    private static Specification<Product> hasOrganization(
            Long organizationId
    ) {
        return (root, query, criteriaBuilder) -> {
            if (organizationId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("organization").get("id"),
                    organizationId
            );
        };
    }
    private static Specification<Product> hasBrand(
            String brand
    ) {
        return (root, query, criteriaBuilder) -> {
            if (brand == null || brand.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String normalizedBrand = brand
                    .trim()
                    .toLowerCase(Locale.ROOT);

            return criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("brand")),
                    normalizedBrand
            );
        };
    }

    /** Leaves out hidden (INACTIVE) products. */
    public static Specification<Product> notHidden() {
        return (root, query, cb) -> cb.notEqual(root.get("status"), ProductStatus.INACTIVE);
    }

    private static Specification<Product> hasStatus(
            ProductStatus status
    ) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("status"),
                    status
            );
        };
    }

    private static Specification<Product> priceGreaterThanOrEqual(
            BigDecimal minPrice
    ) {
        return (root, query, criteriaBuilder) -> {
            if (minPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.get("price"),
                    minPrice
            );
        };
    }

    private static Specification<Product> priceLessThanOrEqual(
            BigDecimal maxPrice
    ) {
        return (root, query, criteriaBuilder) -> {
            if (maxPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.get("price"),
                    maxPrice
            );
        };
    }

    private static String escapeLikePattern(
            String value
    ) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}