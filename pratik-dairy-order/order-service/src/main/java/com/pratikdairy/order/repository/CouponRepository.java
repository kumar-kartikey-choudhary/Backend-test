package com.pratikdairy.order.repository;

import com.pratikdairy.order.model.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, String> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Uses one redemption, atomically. The limit is checked in the same UPDATE that counts the use, so
     * two checkouts racing for the last redemption can't both get it (a read-then-write would let them).
     *
     * @return 1 if the use was counted, 0 if the coupon has already reached its usage limit
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount + 1 "
            + "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)")
    int tryRedeem(@Param("id") String id);

    /** Gives a redemption back (the order that used it was cancelled). Never goes below zero. */
    @Modifying
    @Query("UPDATE Coupon c SET c.usedCount = c.usedCount - 1 WHERE c.id = :id AND c.usedCount > 0")
    int giveBack(@Param("id") String id);
}
