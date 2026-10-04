package com.omnia.backend.service.impl;

import com.omnia.backend.entity.Order;
import com.omnia.backend.entity.OrderStatusHistory;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrderStatus;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerOrderTrackingTest {
    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository histories;
    @Mock CouponRepository coupons;
    @Mock OrderCouponRepository orderCoupons;
    @Mock OrderItemRepository items;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock PaymentRepository payments;
    @Mock OrganizationAccessService access;
    @Mock ApplicationEventPublisher events;
    private OrderServiceImpl service;
    private User owner;
    @BeforeEach void setup() {
        service=new OrderServiceImpl(orders,histories,coupons,orderCoupons,items,products,users,payments,access,events);
        owner=User.builder().id(1L).email("buyer@example.com").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer@example.com",null,List.of()));
        when(users.findByEmail("buyer@example.com")).thenReturn(Optional.of(owner));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void ownerReceivesRealHistoryWithoutStaffIdentity() {
        when(orders.findById(15L)).thenReturn(Optional.of(Order.builder().id(15L).user(owner).build()));
        User staff=User.builder().id(99L).email("private@example.com").build();
        LocalDateTime date=LocalDateTime.of(2026,10,4,12,0);
        when(histories.findAllForOrder(15L)).thenReturn(List.of(
                OrderStatusHistory.builder().id(1L).toStatus(OrderStatus.PENDING).changedAt(date).changedByUser(staff).build(),
                OrderStatusHistory.builder().id(2L).fromStatus(OrderStatus.PENDING).toStatus(OrderStatus.CONFIRMED).changedAt(date.plusMinutes(5)).build()));
        var result=service.getMyOrderStatusHistory(15L);
        assertEquals(2,result.size());assertEquals(OrderStatus.PENDING,result.get(0).getToStatus());
        assertEquals(date.plusMinutes(5),result.get(1).getChangedAt());
        assertNull(result.get(0).getChangedByName());assertNull(result.get(0).getChangedByUserId());
    }
    @Test void anotherCustomersOrderDoesNotExposeHistory() {
        User other=User.builder().id(2L).build();
        when(orders.findById(15L)).thenReturn(Optional.of(Order.builder().id(15L).user(other).build()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getMyOrderStatusHistory(15L));
        verifyNoInteractions(histories);
    }
    @Test void missingOrderDoesNotReadHistory() {
        when(orders.findById(15L)).thenReturn(Optional.empty());
        assertThrows(com.omnia.backend.common.exception.ResourceNotFoundException.class,()->service.getMyOrderStatusHistory(15L));
        verifyNoInteractions(histories);
    }
}
