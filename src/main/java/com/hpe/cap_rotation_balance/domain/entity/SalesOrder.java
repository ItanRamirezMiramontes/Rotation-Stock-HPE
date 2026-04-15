package com.hpe.cap_rotation_balance.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hpe.cap_rotation_balance.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sales_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesOrder {

    @Id
    @Column(name = "hpe_order_id", length = 20)
    private String hpeOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_status")
    private OrderStatus headerStatus;

    private String omRegion;
    private String sorg;
    private String salesOffice;
    private String salesGroup;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate entryDate;

    @Column(name = "cust_po_ref")
    private String custPoRef;

    private String shipToAddress;

    private String rtm;

    @Enumerated(EnumType.STRING)
    private FiscalQuarter fiscalQuarter;

    private Integer fiscalYear;

    @Column(precision = 19, scale = 4)
    private BigDecimal netValueItem;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private String orderReason;

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({"orders", "hibernateLazyInitializer", "handler"})
    private Customer customer;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = OffsetDateTime.now(); this.updatedAt = OffsetDateTime.now(); }
}