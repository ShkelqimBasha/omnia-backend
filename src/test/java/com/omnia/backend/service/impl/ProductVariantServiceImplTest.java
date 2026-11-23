package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.ProductVariantRequest;
import com.omnia.backend.dto.response.ProductVariantResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.ProductVariant;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.repository.CartItemRepository;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.ProductVariantRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Uses the real {@link OrganizationAccessService} and
 * {@link CurrentUserService} (only repositories are mocked),
 * so the OWNER / ADMIN / STAFF / stranger / platform-admin
 * rules are exercised end to end.
 */
@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    private static final long ORGANIZATION_ID = 7L;
    private static final long CATEGORY_ID = 3L;
    private static final long PRODUCT_ID = 10L;
    private static final long VARIANT_ID = 100L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private OrganizationCategoryPermissionRepository permissionRepository;

    private ProductVariantServiceImpl variantService;

    private Organization organization;
    private Product product;
    private ProductVariant variant;
    private ProductVariantRequest request;
    private User currentUser;

    @BeforeEach
    void setUp() {

        CurrentUserService currentUserService =
                new CurrentUserService(userRepository);

        OrganizationAccessService accessService =
                new OrganizationAccessService(
                        currentUserService,
                        memberRepository,
                        permissionRepository
                );

        variantService = new ProductVariantServiceImpl(
                productRepository,
                variantRepository,
                accessService,
                currentUserService,
                cartItemRepository
        );

        organization = Organization.builder()
                .id(ORGANIZATION_ID)
                .name("Shop")
                .slug("shop")
                .status(OrganizationStatus.ACTIVE)
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .name("Samsung Galaxy S24")
                .organization(organization)
                .category(Category.builder().id(CATEGORY_ID).name("Veshje")
                        .kind(CategoryKind.CLOTHING).build())
                .stock(15)
                .build();

        request = ProductVariantRequest.builder()
                .variantName("SIZE")
                .variantValue("S")
                .stock(4)
                .build();

        variant = ProductVariant.builder()
                .id(VARIANT_ID)
                .product(product)
                .variantName("SIZE")
                .variantValue("M")
                .priceAdjustment(BigDecimal.ZERO)
                .stock(15)
                .build();
        product.getVariants().add(variant);

        lenient().when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // addVariant
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(
            value = OrganizationMemberRole.class,
            names = {"OWNER", "ADMIN"}
    )
    void addVariant_OrganizationManagerWithGrant_ShouldSave(
            OrganizationMemberRole role
    ) {
        loginAsMember(role);
        grantCategory(true);
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(variantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> {
                    ProductVariant saved = invocation.getArgument(0);
                    saved.setId(VARIANT_ID);
                    return saved;
                });

        ProductVariantResponse result =
                variantService.addVariant(PRODUCT_ID, request);

        assertEquals(VARIANT_ID, result.getId());
        assertEquals(PRODUCT_ID, result.getProductId());
        assertEquals("SIZE", result.getVariantName());
        assertEquals("S", result.getVariantValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPriceAdjustment()));
        assertEquals(4, result.getStock());
        assertEquals(19, product.getStock(), "product stock is the sum of its sizes");

        ArgumentCaptor<ProductVariant> captor =
                ArgumentCaptor.forClass(ProductVariant.class);
        verify(variantRepository).save(captor.capture());
        assertSame(product, captor.getValue().getProduct());
    }

    @Test
    void addVariant_Staff_ShouldBeDenied() {
        loginAsMember(OrganizationMemberRole.STAFF);
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_Stranger_ShouldBeDenied() {
        loginAsStranger();
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_OwnerWithoutCategoryGrant_ShouldBeDenied() {
        loginAsMember(OrganizationMemberRole.OWNER);
        when(permissionRepository.findByOrganizationIdAndCategoryIdAndStatus(
                ORGANIZATION_ID,
                CATEGORY_ID,
                OrganizationPermissionStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_OwnerWhoseGrantForbidsUpdate_ShouldBeDenied() {
        loginAsMember(OrganizationMemberRole.OWNER);
        grantCategory(false);
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_OwnerOfInactiveOrganization_ShouldBeDenied() {
        organization.setStatus(OrganizationStatus.INACTIVE);
        loginAsMember(OrganizationMemberRole.OWNER);
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_PlatformAdmin_ShouldSaveWithoutMembership() {
        loginAsPlatformAdmin();
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(variantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(variantService.addVariant(PRODUCT_ID, request));

        verify(variantRepository).save(any(ProductVariant.class));
        verifyNoInteractions(memberRepository, permissionRepository);
    }

    @Test
    void addVariant_ProductWithoutOrganization_ShouldOnlyAllowPlatformAdmin() {
        product.setOrganization(null);
        loginAsStranger();
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.addVariant(PRODUCT_ID, request)
        );

        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_ShouldTrimTextAndDefaultMissingPriceAdjustment() {
        loginAsPlatformAdmin();
        request.setVariantName("  size ");
        request.setVariantValue(" xl  ");
        request.setPriceAdjustment(null);
        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));
        when(variantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductVariantResponse result =
                variantService.addVariant(PRODUCT_ID, request);

        assertEquals("SIZE", result.getVariantName());
        assertEquals("XL", result.getVariantValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPriceAdjustment()));
    }

    @Test
    void addVariant_shouldThrowWhenProductDoesNotExist() {
        when(productRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.addVariant(99L, request)
        );

        assertEquals("Product not found", exception.getMessage());
        verify(variantRepository, never()).save(any(ProductVariant.class));
    }

    // ------------------------------------------------------------------
    // getProductVariants (public read, unchanged)
    // ------------------------------------------------------------------

    @Test
    void getProductVariants_ShouldNotRequireAuthorization() {
        ProductVariant secondVariant = ProductVariant.builder()
                .id(101L)
                .product(product)
                .variantName("Storage")
                .variantValue("256GB")
                .priceAdjustment(new BigDecimal("100.00"))
                .stock(8)
                .build();

        when(variantRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of(variant, secondVariant));

        List<ProductVariantResponse> result =
                variantService.getProductVariants(PRODUCT_ID);

        assertEquals(2, result.size());
        assertEquals("SIZE", result.get(0).getVariantName());
        assertEquals("256GB", result.get(1).getVariantValue());
        verifyNoInteractions(userRepository, memberRepository, permissionRepository);
    }

    @Test
    void getProductVariants_shouldReturnEmptyList() {
        when(variantRepository.findByProductId(PRODUCT_ID))
                .thenReturn(List.of());

        assertTrue(variantService.getProductVariants(PRODUCT_ID).isEmpty());
    }

    // ------------------------------------------------------------------
    // updateVariant
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(
            value = OrganizationMemberRole.class,
            names = {"OWNER", "ADMIN"}
    )
    void updateVariant_OrganizationManagerWithGrant_ShouldUpdate(
            OrganizationMemberRole role
    ) {
        loginAsMember(role);
        grantCategory(true);
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));
        when(variantRepository.save(variant)).thenReturn(variant);

        ProductVariantResponse result = variantService.updateVariant(
                VARIANT_ID,
                updateRequest()
        );

        assertEquals("SIZE", result.getVariantName());
        assertEquals("L", result.getVariantValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPriceAdjustment()));
        assertEquals(6, result.getStock());
        assertEquals(6, product.getStock());
        verify(variantRepository).save(variant);
    }

    @Test
    void updateVariant_PlatformAdmin_ShouldUpdate() {
        loginAsPlatformAdmin();
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));
        when(variantRepository.save(variant)).thenReturn(variant);

        variantService.updateVariant(VARIANT_ID, updateRequest());

        assertEquals("L", variant.getVariantValue());
        verifyNoInteractions(memberRepository, permissionRepository);
    }

    @Test
    void updateVariant_Staff_ShouldBeDeniedAndLeaveVariantUnchanged() {
        loginAsMember(OrganizationMemberRole.STAFF);
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.updateVariant(VARIANT_ID, updateRequest())
        );

        assertEquals("M", variant.getVariantValue());
        assertEquals(15, variant.getStock());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void updateVariant_Stranger_ShouldBeDeniedAndLeaveVariantUnchanged() {
        loginAsStranger();
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.updateVariant(VARIANT_ID, updateRequest())
        );

        assertEquals("M", variant.getVariantValue());
        verify(variantRepository, never()).save(any());
    }

    @Test
    void updateVariant_shouldThrowWhenVariantDoesNotExist() {
        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.updateVariant(99L, request)
        );

        assertEquals("Variant not found", exception.getMessage());
        verify(variantRepository, never()).save(any(ProductVariant.class));
    }

    // ------------------------------------------------------------------
    // deleteVariant
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(
            value = OrganizationMemberRole.class,
            names = {"OWNER", "ADMIN"}
    )
    void deleteVariant_OrganizationManagerWithGrant_ShouldDelete(
            OrganizationMemberRole role
    ) {
        loginAsMember(role);
        grantCategory(true);
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        variantService.deleteVariant(VARIANT_ID);

        verify(variantRepository).delete(variant);
    }

    @Test
    void deleteVariant_PlatformAdmin_ShouldDelete() {
        loginAsPlatformAdmin();
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        variantService.deleteVariant(VARIANT_ID);

        verify(variantRepository).delete(variant);
    }

    @Test
    void deleteVariant_Staff_ShouldBeDenied() {
        loginAsMember(OrganizationMemberRole.STAFF);
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.deleteVariant(VARIANT_ID)
        );

        verify(variantRepository, never()).delete(any());
    }

    @Test
    void deleteVariant_Stranger_ShouldBeDenied() {
        loginAsStranger();
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        assertThrows(
                AccessDeniedException.class,
                () -> variantService.deleteVariant(VARIANT_ID)
        );

        verify(variantRepository, never()).delete(any());
    }

    @Test
    void deleteVariant_shouldThrowWhenVariantDoesNotExist() {
        when(variantRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> variantService.deleteVariant(99L)
        );

        assertEquals("Variant not found", exception.getMessage());
        verify(variantRepository, never()).delete(any(ProductVariant.class));
    }

    // ------------------------------------------------------------------
    // size rules
    // ------------------------------------------------------------------

    @Test
    void addVariant_ShouldRefuseNamesOtherThanSize() {
        loginAsPlatformAdmin();
        request.setVariantName("Color");

        assertThrows(IllegalArgumentException.class,
                () -> variantService.addVariant(PRODUCT_ID, request));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_ShouldRefuseValuesNotAllowedForTheCategory() {
        loginAsPlatformAdmin();
        request.setVariantValue("42");

        assertThrows(IllegalArgumentException.class,
                () -> variantService.addVariant(PRODUCT_ID, request));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_ShouldRefuseCategoriesWithoutSizes() {
        loginAsPlatformAdmin();
        product.getCategory().setKind(CategoryKind.GENERAL);

        assertThrows(IllegalArgumentException.class,
                () -> variantService.addVariant(PRODUCT_ID, request));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_ShouldRefuseADuplicateSize() {
        loginAsPlatformAdmin();
        request.setVariantValue("m");

        assertThrows(IllegalArgumentException.class,
                () -> variantService.addVariant(PRODUCT_ID, request));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void addVariant_ShouldRefuseAPriceAdjustment() {
        loginAsPlatformAdmin();
        request.setPriceAdjustment(new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class,
                () -> variantService.addVariant(PRODUCT_ID, request));
        verify(variantRepository, never()).save(any());
    }

    @Test
    void deleteVariant_InAnOpenCart_ShouldKeepTheSizeSoldOut() {
        loginAsPlatformAdmin();
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));
        when(cartItemRepository.existsByVariantId(VARIANT_ID)).thenReturn(true);

        variantService.deleteVariant(VARIANT_ID);

        verify(variantRepository, never()).delete(any());
        assertEquals(0, variant.getStock());
        assertEquals(0, product.getStock());
    }

    @Test
    void deleteVariant_ShouldResumTheProductStock() {
        loginAsPlatformAdmin();
        ProductVariant large = ProductVariant.builder().id(101L).product(product)
                .variantName("SIZE").variantValue("L").stock(3).build();
        product.getVariants().add(large);
        when(variantRepository.findById(VARIANT_ID))
                .thenReturn(Optional.of(variant));

        variantService.deleteVariant(VARIANT_ID);

        verify(variantRepository).delete(variant);
        assertEquals(3, product.getStock());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ProductVariantRequest updateRequest() {
        return ProductVariantRequest.builder()
                .variantName("SIZE")
                .variantValue("L")
                .stock(6)
                .build();
    }

    private void login(User user, String authority) {
        currentUser = user;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
        lenient().when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    private void loginAsStranger() {
        login(user(50L, "stranger@example.com", "USER"), "ROLE_USER");
        lenient().when(memberRepository.findByOrganizationIdAndUserIdAndStatus(
                ORGANIZATION_ID,
                50L,
                OrganizationMemberStatus.ACTIVE
        )).thenReturn(Optional.empty());
    }

    private void loginAsPlatformAdmin() {
        login(user(1L, "admin@example.com", "ADMIN"), "ROLE_ADMIN");
    }

    private void loginAsMember(OrganizationMemberRole role) {
        login(user(60L, "member@example.com", "USER"), "ROLE_USER");

        OrganizationMember member = OrganizationMember.builder()
                .id(600L)
                .organization(organization)
                .user(currentUser)
                .membershipRole(role)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();

        lenient().when(memberRepository.findByOrganizationIdAndUserIdAndStatus(
                ORGANIZATION_ID,
                60L,
                OrganizationMemberStatus.ACTIVE
        )).thenReturn(Optional.of(member));
    }

    private void grantCategory(boolean canUpdate) {
        OrganizationCategoryPermission permission =
                OrganizationCategoryPermission.builder()
                        .organization(organization)
                        .category(product.getCategory())
                        .canUpdate(canUpdate)
                        .status(OrganizationPermissionStatus.ACTIVE)
                        .build();

        when(permissionRepository.findByOrganizationIdAndCategoryIdAndStatus(
                ORGANIZATION_ID,
                CATEGORY_ID,
                OrganizationPermissionStatus.ACTIVE
        )).thenReturn(Optional.of(permission));
    }

    private static User user(long id, String email, String roleName) {
        return User.builder()
                .id(id)
                .email(email)
                .role(Role.builder().name(roleName).build())
                .build();
    }
}
