package com.hpe.cap_rotation_balance.domain.entity;

import com.hpe.cap_rotation_balance.domain.enums.Currency;
import com.hpe.cap_rotation_balance.domain.enums.FiscalQuarter;
import com.hpe.cap_rotation_balance.domain.enums.OrderStatus;
import com.hpe.cap_rotation_balance.domain.enums.OrderType;
import com.hpe.cap_rotation_balance.entity.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sales_orders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SalesOrder {

    @Id
    @Column(name = "hpe_order_id", length = 20)
    private String hpeOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "header_status", length = 10)
    private OrderStatus headerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 10)
    private OrderType orderType;

    @Column(name = "order_reason", length = 15)
    private String orderReason;

    @Column(name = "entry_date", nullable = false) //nullable nos dice que no debemos tener datos vacios
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_quarter", length = 5, nullable = false)
    private FiscalQuarter fiscalQuarter;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    //preciosion es el numero total de digitos que puede tener el numero
    //scale es cuantos espacios tiene despues del punto decimal

    @Column(name = "net_value_item", precision = 19, scale = 4, nullable = false)
    private BigDecimal netValueItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 5, nullable = false)
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;


    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}