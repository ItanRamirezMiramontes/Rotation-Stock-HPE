package com.hpe.cap_rotation_balance.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sales_orders")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class SalesOrder {

    @Id
    @Column(name = "hpe_order_id", length = 20)
    private String hpeOrderId;

    @Column(name = "int_header_status", length = 20)
    private String headerStatus;

    @Column(name = "invoice_header_status")
    private String invoiceHeaderStatus;

    @Column(name = "om_region")
    private String omRegion;

    private String sorg;
    private String salesOffice;
    private String salesGroup;

    @Column(name = "otyp")
    private String orderType;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryDate;

    private String custPoRef;

    @Column(length = 500)
    private String shipToAddress;

    private String rtm;
    private String currency;

    @Column(name = "order_value", precision = 19, scale = 4)
    private BigDecimal orderValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sold_to_party")
    @JsonIgnoreProperties({"orders", "hibernateLazyInitializer", "handler"})
    private Customer customer;

    // --- CAMPO DE AUDITORÍA INTERNA REQUERIDO POR EL SERVICE ---
    @Column(name = "internal_status")
    private String internalStatus; // Ejemplo: "LOADED", "PRICE_SYNCED"

    // Periodos informativos para el Final Report
    private String fiscalQuarter;
    private Integer fiscalYear;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
        if (this.orderType == null) this.orderType = "ZRES";
    }
}