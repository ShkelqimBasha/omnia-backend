package com.omnia.backend.catalog;

import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;

import java.util.List;
import java.util.Objects;

/**
 * Stock rules shared by the cart, checkout and order cancellation.
 *
 * <p>A product with sizes (see {@link ProductSizes#sizesOf(Product)}) keeps
 * stock per size, and its own stock is always the sum of its sizes. A product
 * without sizes keeps working exactly as before: one stock on the product.
 *
 * <p>Callers that change stock must hold the product's row lock
 * ({@code ProductRepository.findByIdForUpdate}); size rows are only written
 * while their product is locked.
 */
public final class SizeStock {

    private SizeStock() {
    }

    /**
     * The size a buyer chose for this product: {@code null} for a product
     * without sizes. Refuses a missing size on a sized product, a size on a
     * product without sizes, and a size of another product.
     */
    public static ProductVariant chosenSize(Product product, Long variantId) {
        List<ProductVariant> sizes = ProductSizes.sizesOf(product);
        if (sizes.isEmpty()) {
            if (variantId != null) {
                throw new IllegalArgumentException("Ky produkt nuk ka masa: " + product.getName());
            }
            return null;
        }
        if (variantId == null) {
            throw new IllegalArgumentException("Zgjidh masën për produktin: " + product.getName());
        }
        return sizes.stream()
                .filter(size -> Objects.equals(size.getId(), variantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Masa e zgjedhur nuk i përket produktit: " + product.getName()));
    }

    /** Stock a cart or checkout line may take: the size's stock, else the product's. */
    public static int available(Product product, ProductVariant size) {
        Integer stock = size != null ? size.getStock() : product.getStock();
        return stock == null ? 0 : stock;
    }

    /** Refuses a quantity above what the chosen size (or the unsized product) has left. */
    public static void requireAvailable(Product product, ProductVariant size, int quantity) {
        int available = available(product, size);
        if (quantity <= available) return;
        if (size == null) {
            throw new IllegalArgumentException("Insufficient stock for product: " + product.getName());
        }
        String label = ProductSizes.label(size.getVariantValue());
        throw new IllegalArgumentException(available <= 0
                ? "Masa " + label + " ka mbaruar për produktin: " + product.getName()
                : "Nuk ka mjaftueshëm sasi për masën " + label + " (mbeten " + available
                        + ") për produktin: " + product.getName());
    }

    /**
     * Checks and takes stock for one line of a locked product. Returns the
     * chosen size, or {@code null} for a product without sizes.
     */
    public static ProductVariant reserve(Product product, Long variantId, int quantity) {
        ProductVariant size = chosenSize(product, variantId);
        requireAvailable(product, size, quantity);
        if (size == null) {
            product.setStock(available(product, null) - quantity);
        } else {
            size.setStock(available(product, size) - quantity);
            syncProductStock(product);
        }
        return size;
    }

    /**
     * Puts back the stock of a cancelled order line on a locked product. The
     * line's size gets it back. When the size no longer exists (deleted, so the
     * line's variant_id is NULL), a product with a single size gets it on that
     * size (this covers orders placed before sizes existed); with several sizes
     * there is no way to know which, so nothing is put back.
     *
     * @return whether stock was put back
     */
    public static boolean restore(Product product, Long variantId, int quantity) {
        List<ProductVariant> sizes = ProductSizes.sizesOf(product);
        if (sizes.isEmpty()) {
            product.setStock(available(product, null) + quantity);
            return true;
        }
        ProductVariant target = sizes.stream()
                .filter(size -> variantId != null && Objects.equals(size.getId(), variantId))
                .findFirst()
                .orElse(sizes.size() == 1 ? sizes.get(0) : null);
        if (target == null) return false;
        target.setStock(available(product, target) + quantity);
        syncProductStock(product);
        return true;
    }

    /** Product stock = sum of its sizes; unchanged for a product without sizes. */
    public static void syncProductStock(Product product) {
        List<ProductVariant> sizes = ProductSizes.sizesOf(product);
        if (!sizes.isEmpty()) product.setStock(ProductSizes.totalStock(sizes));
    }
}
