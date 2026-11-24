package kz.edu.biletflow.backend.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Sales paid on one day (Asia/Almaty), refunded ones included. Days without sales are not listed.")
public record DailySalesResponse(
        @Schema(description = "Payment day, Asia/Almaty", example = "2026-11-24") LocalDate date,
        @Schema(description = "Orders paid that day (with ticketTypeId: orders containing that type)") long paidOrders,
        @Schema(description = "Tickets in those orders (with ticketTypeId: only that type's)") long ticketsSold,
        @Schema(description = "Gross revenue in KZT, before promo discounts and refunds") BigDecimal revenue) {
}
