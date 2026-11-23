package com.omnia.backend.integration;

import com.jayway.jsonpath.JsonPath;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.Product;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.enums.ProductStatus;
import com.omnia.backend.event.InquiryCreatedEvent;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.ProductRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Inquiry status workflow, platform admin list, property visit slots and the company email. */
class InquiryWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String BUYER = "inquiry-buyer@example.com";
    private static final String BUYER2 = "inquiry-buyer2@example.com";
    private static final String OWNER = "inquiry-owner@example.com";
    private static final String STRANGER = "inquiry-stranger@example.com";
    private static final String ADMIN = "inquiry-admin@example.com";

    @MockitoBean private EmailService emailService;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Organization seller;
    private Category cars;
    private Category property;
    private Product car;
    private Product flat;
    private OrganizationMember ownerMember;

    @BeforeEach
    void setUp() {
        for (String email : List.of(BUYER, BUYER2, OWNER, STRANGER, ADMIN)) {
            if (userRepository.findByEmail(email).isEmpty()) {
                String role = email.equals(ADMIN) ? "ADMIN" : "USER";
                userRepository.saveAndFlush(User.builder().firstName("Test").lastName("Kërkesa")
                        .username(email.substring(0, email.indexOf('@')).replace('-', '_'))
                        .email(email).passwordHash("hash")
                        .role(roleRepository.findByName(role).orElseThrow()).emailVerified(true).build());
            }
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = organizationRepository.save(Organization.builder().name("Prona Shpk " + suffix)
                .slug("prona-shpk-" + suffix).status(OrganizationStatus.ACTIVE)
                .phone("+355 69 000 0001").businessEmail("zyra@example.com").build());
        ownerMember = memberRepository.save(OrganizationMember.builder().organization(seller)
                .user(userRepository.findByEmail(OWNER).orElseThrow())
                .membershipRole(OrganizationMemberRole.OWNER).status(OrganizationMemberStatus.ACTIVE).build());
        cars = categoryRepository.save(Category.builder().name("Makina " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.CAR).sortOrder(4).build());
        property = categoryRepository.save(Category.builder().name("Prona " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.PROPERTY).sortOrder(5).build());
        car = productRepository.save(listing(cars, "Golf 7"));
        flat = productRepository.save(listing(property, "Apartament 2+1 në Bllok"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM push_outbox WHERE notification_id IN "
                + "(SELECT id FROM notification_inbox WHERE event_key LIKE 'inquiry:%')");
        jdbcTemplate.update("DELETE FROM notification_inbox WHERE event_key LIKE 'inquiry:%'");
        jdbcTemplate.update("DELETE FROM product_visit_slots WHERE product_id IN (?, ?)", car.getId(), flat.getId());
        jdbcTemplate.update("DELETE FROM product_inquiries WHERE organization_id = ?", seller.getId());
        productRepository.deleteAll(List.of(car, flat));
        categoryRepository.deleteAll(List.of(cars, property));
        memberRepository.delete(ownerMember);
        organizationRepository.delete(seller);
    }

    private Product listing(Category category, String name) {
        return Product.builder().category(category).organization(seller).name(name)
                .price(new BigDecimal("1000")).stock(1).status(ProductStatus.ACTIVE)
                .attributes(new java.util.HashMap<>()).build();
    }

    private static RequestPostProcessor as(String email) {
        return user(email).roles(email.equals(ADMIN) ? "ADMIN" : "USER");
    }

    private long createInquiry(String email, Product product, String body) throws Exception {
        String json = mockMvc.perform(post("/api/products/{id}/inquiries", product.getId()).with(as(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String visit(String extra) {
        return "{\"type\":\"VISIT\",\"name\":\"Ana\",\"phone\":\"+355691112233\"" + extra + "}";
    }

    private static final String CONTACT = "{\"type\":\"CONTACT\",\"name\":\"Beni\",\"phone\":\"0692223344\"}";

    @Test
    void sellerMovesAnInquiryThroughTheWorkflowAndAdminFiltersAllCompanies() throws Exception {
        long visitId = createInquiry(BUYER, flat, visit(",\"preferredDate\":\"" + LocalDate.now().plusDays(4) + "\""));
        long contactId = createInquiry(BUYER, car, CONTACT);
        String statusUrl = "/api/organizations/{org}/inquiries/{id}/status";

        mockMvc.perform(patch(statusUrl, seller.getId(), visitId).with(as(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.statusUpdatedAt").isNotEmpty());
        mockMvc.perform(patch(statusUrl, seller.getId(), visitId).with(as(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VISIT_SCHEDULED\"}"))
                .andExpect(jsonPath("$.status").value("VISIT_SCHEDULED"));
        mockMvc.perform(patch(statusUrl, seller.getId(), visitId).with(as(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(statusUrl, seller.getId(), visitId).with(as(STRANGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(statusUrl, seller.getId() + 100_000, visitId).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/organizations/{org}/inquiries", seller.getId()).with(as(OWNER))
                        .param("status", "VISIT_SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(visitId))
                .andExpect(jsonPath("$.content[0].categoryKind").value("PROPERTY"));

        String admin = "/api/admin/inquiries";
        mockMvc.perform(get(admin).with(as(ADMIN)).param("organizationId", seller.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].organizationName").value(seller.getName()));
        mockMvc.perform(get(admin).with(as(ADMIN)).param("organizationId", seller.getId().toString())
                        .param("kind", "CAR").param("status", "NEW"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(contactId));
        mockMvc.perform(get(admin).with(as(ADMIN)).param("organizationId", seller.getId().toString())
                        .param("kind", "PROPERTY").param("type", "VISIT").param("status", "NEW"))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(admin).with(as(ADMIN)).param("kind", "BOAT"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(admin).with(as(OWNER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void buyerBooksASellerVisitSlotOnceAndClosingFreesIt() throws Exception {
        LocalDateTime time = LocalDate.now(ZoneId.of("Europe/Tirane")).plusDays(2).atTime(10, 30);
        String slotsUrl = "/api/manage/products/{id}/visit-slots";
        String slotBody = "{\"startsAt\":\"" + time + "\"}";

        String created = mockMvc.perform(post(slotsUrl, flat.getId()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booked").value(false))
                .andReturn().getResponse().getContentAsString();
        long slotId = ((Number) JsonPath.read(created, "$.id")).longValue();
        mockMvc.perform(post(slotsUrl, flat.getId()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody))
                .andExpect(status().isConflict());
        mockMvc.perform(post(slotsUrl, flat.getId()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"" + time.minusDays(5) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(slotsUrl, car.getId()).with(as(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(slotsUrl, flat.getId()).with(as(STRANGER))
                        .contentType(MediaType.APPLICATION_JSON).content(slotBody))
                .andExpect(status().isForbidden());

        String publicUrl = "/api/products/{id}/visit-slots";
        mockMvc.perform(get(publicUrl, flat.getId()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(slotId))
                .andExpect(jsonPath("$[0].bookedBy").doesNotExist());

        mockMvc.perform(post("/api/products/{id}/inquiries", car.getId()).with(as(BUYER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONTACT\",\"name\":\"Ana\",\"phone\":\"0691112233\",\"visitSlotId\":"
                                + slotId + "}"))
                .andExpect(status().isBadRequest());

        String booked = mockMvc.perform(post("/api/products/{id}/inquiries", flat.getId()).with(as(BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(visit(",\"visitSlotId\":" + slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitAt", org.hamcrest.Matchers.startsWith(time.toString())))
                .andExpect(jsonPath("$.preferredDate").value(time.toLocalDate().toString()))
                .andReturn().getResponse().getContentAsString();
        long inquiryId = ((Number) JsonPath.read(booked, "$.id")).longValue();

        mockMvc.perform(get(publicUrl, flat.getId()).with(anonymous()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/products/{id}/inquiries", flat.getId()).with(as(BUYER2))
                        .contentType(MediaType.APPLICATION_JSON).content(visit(",\"visitSlotId\":" + slotId)))
                .andExpect(status().isConflict());
        mockMvc.perform(get(slotsUrl, flat.getId()).with(as(ADMIN)))
                .andExpect(jsonPath("$[0].booked").value(true))
                .andExpect(jsonPath("$[0].inquiryId").value(inquiryId))
                .andExpect(jsonPath("$[0].bookedBy").value("Ana"));
        mockMvc.perform(delete(slotsUrl + "/{slot}", flat.getId(), slotId).with(as(ADMIN)))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/organizations/{org}/inquiries/{id}/status", seller.getId(), inquiryId)
                        .with(as(OWNER)).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.visitAt").isNotEmpty());
        mockMvc.perform(get(publicUrl, flat.getId()).with(anonymous()))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(delete(slotsUrl + "/{slot}", flat.getId(), slotId).with(as(ADMIN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(publicUrl, flat.getId()).with(anonymous()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sellerIsNotifiedAndTheCompanyGetsAnEmail() throws Exception {
        createInquiry(BUYER, flat, visit(",\"message\":\"Të shtunën?\""));
        Long ownerId = userRepository.findByEmail(OWNER).orElseThrow().getId();
        assertEquals("Apartament 2+1 në Bllok", jdbcTemplate.queryForObject(
                "SELECT label FROM notification_inbox WHERE recipient_id = ? AND kind = 'INQUIRY_VISIT' "
                        + "AND target_id = ?", String.class, ownerId, flat.getId()));

        ArgumentCaptor<InquiryCreatedEvent> event = ArgumentCaptor.forClass(InquiryCreatedEvent.class);
        verify(emailService, timeout(2000)).sendInquiryEmail(eq("zyra@example.com"), event.capture());
        assertEquals("VISIT", event.getValue().type());
        assertEquals("Apartament 2+1 në Bllok", event.getValue().productName());
        assertEquals("Ana", event.getValue().contactName());
        assertEquals("Të shtunën?", event.getValue().message());
    }
}
