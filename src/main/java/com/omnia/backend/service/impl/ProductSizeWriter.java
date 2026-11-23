package com.omnia.backend.service.impl;

import com.omnia.backend.catalog.ProductSizes;
import com.omnia.backend.catalog.ProductSizes.SizeInput;
import com.omnia.backend.catalog.SizeStock;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.repository.CartItemRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the sizes of a product (its SIZE variant rows). The caller holds the
 * product's row lock and has already set the product's category and checked
 * the list with {@link ProductSizes#normalize}.
 */
@Component
public class ProductSizeWriter {

    private final ProductVariantRepository variantRepository;
    private final CartItemRepository cartItemRepository;

    public ProductSizeWriter(ProductVariantRepository variantRepository,
                             CartItemRepository cartItemRepository) {
        this.variantRepository = variantRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Makes the product's sizes equal to {@code sizes} (already normalized).
     * A size that stays keeps its id, so cart lines pointing at it stay valid.
     * A removed size is deleted, unless an open cart line points at it: then it
     * stays with stock 0 so the buyer sees it as sold out. An empty list makes
     * the product unsized: all sizes are deleted, with the cart lines that
     * chose one (they could never be checked out again).
     *
     * <p>Afterwards the product's stock is the sum of its sizes; with an empty
     * list the caller sets the stock.
     */
    public void replace(Product product, List<SizeInput> sizes) {
        Map<String, ProductVariant> existing = new LinkedHashMap<>();
        for (ProductVariant variant : product.getVariants()) {
            if (ProductSizes.isSize(variant)) existing.put(variant.getVariantValue(), variant);
        }

        for (SizeInput size : sizes) {
            ProductVariant variant = existing.remove(size.value());
            if (variant == null) {
                variant = variantRepository.save(ProductVariant.builder()
                        .product(product)
                        .variantName(ProductSizes.VARIANT_NAME)
                        .variantValue(size.value())
                        .priceAdjustment(BigDecimal.ZERO)
                        .stock(size.stock())
                        .build());
                product.getVariants().add(variant);
            } else {
                variant.setStock(size.stock());
            }
        }

        boolean unsized = sizes.isEmpty();
        for (ProductVariant removed : new ArrayList<>(existing.values())) {
            if (!unsized && cartItemRepository.existsByVariantId(removed.getId())) {
                removed.setStock(0);
                continue;
            }
            if (unsized) cartItemRepository.deleteByVariantId(removed.getId());
            product.getVariants().remove(removed);
            variantRepository.delete(removed);
        }
        variantRepository.flush();

        SizeStock.syncProductStock(product);
    }
}
