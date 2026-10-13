package com.pratikdairy.order.repository;

import com.pratikdairy.order.enums.OrderStatus;
import com.pratikdairy.order.model.Coupon;
import com.pratikdairy.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    @Query("SELECT o FROM ORDER_HEADER o WHERE o.username= :username" )
    List<Order> findByUsername(@Param("username") String username);

    /** How many of this customer's orders still count against a per-customer coupon limit. */
    long countByUsernameAndCouponAndStatusNot(String username, Coupon coupon, OrderStatus status);

    /** Orders still waiting at a status since before the cutoff - candidates for the unpaid-order sweep. */
    List<Order> findByStatusAndOrderDateTimeBefore(OrderStatus status, LocalDateTime cutoff);
}
