package com.hpe.cap_rotation_balance.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", length = 50)
    private String customerId; // Sold To Party ID

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "last_data_ingestion")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<SalesOrder> orders = new ArrayList<>();

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}