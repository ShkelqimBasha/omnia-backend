package com.omnia.backend.repository;

import com.omnia.backend.entity.ProductVisitSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductVisitSlotRepository extends JpaRepository<ProductVisitSlot, Long> {

    /** Free slots a buyer can pick. */
    @Query("select s from ProductVisitSlot s where s.product.id = :productId and s.inquiry is null "
            + "and s.startsAt > :from and s.startsAt <= :to order by s.startsAt")
    List<ProductVisitSlot> findAvailable(@Param("productId") Long productId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /** Seller view: every upcoming slot, booked or not. */
    @Query("select s from ProductVisitSlot s left join fetch s.inquiry where s.product.id = :productId "
            + "and s.startsAt > :from order by s.startsAt")
    List<ProductVisitSlot> findUpcoming(@Param("productId") Long productId, @Param("from") LocalDateTime from);

    @Query("select count(s) from ProductVisitSlot s where s.product.id = :productId and s.startsAt > :from")
    long countUpcoming(@Param("productId") Long productId, @Param("from") LocalDateTime from);

    boolean existsByProductIdAndStartsAt(Long productId, LocalDateTime startsAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductVisitSlot s where s.id = :id and s.product.id = :productId")
    Optional<ProductVisitSlot> lockForBooking(@Param("id") Long id, @Param("productId") Long productId);

    Optional<ProductVisitSlot> findByInquiryId(Long inquiryId);
}
