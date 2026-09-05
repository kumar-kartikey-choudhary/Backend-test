package com.pratikdairy.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pratikdairy.parent.base.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Was entirely missing before - User had no addresses at all, which is a hard blocker for a
// delivery-based business: an order had nowhere to ship to. A customer can have several (home,
// work, ...), one marked default for pre-filling checkout.
@Entity
@Table(name = "ADDRESS")
@Data
@EqualsAndHashCode(callSuper = false)
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    @JsonIgnore
    private User user;

    // Free-text label the customer picks, e.g. "Home", "Work", "Mom's place".
    @NotNull
    @Column(name = "LABEL", columnDefinition = "VARCHAR(30) NOT NULL", nullable = false)
    private String label;

    @NotNull
    @Column(name = "LINE1", columnDefinition = "VARCHAR(200) NOT NULL", nullable = false)
    private String line1;

    @Column(name = "LINE2", columnDefinition = "VARCHAR(200)")
    private String line2;

    @NotNull
    @Column(name = "CITY", columnDefinition = "VARCHAR(60) NOT NULL", nullable = false)
    private String city;

    @NotNull
    @Column(name = "STATE", columnDefinition = "VARCHAR(60) NOT NULL", nullable = false)
    private String state;

    @NotNull
    @Column(name = "PINCODE", columnDefinition = "VARCHAR(10) NOT NULL", nullable = false)
    private String pincode;

    @Column(name = "LANDMARK", columnDefinition = "VARCHAR(200)")
    private String landmark;

    // Optional - lets a delivery-routing feature place the address on a map later without a
    // schema change. Null is fine; not every address needs to be geocoded.
    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "IS_DEFAULT", columnDefinition = "TINYINT(1) DEFAULT '0'")
    private boolean isDefault = false;
}