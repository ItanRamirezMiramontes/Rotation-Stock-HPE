package com.hpe.cap_rotation_balance.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(name = "customer_id", length = 20)
    private String customerId;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(length = 3)
    private String country;

    @Column(name = "group_id_country")
    private Long groupIdCountry;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<SalesOrder> orders = new ArrayList<>();
}