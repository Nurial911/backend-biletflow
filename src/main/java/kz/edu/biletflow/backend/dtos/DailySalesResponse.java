package kz.edu.biletflow.backend.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesResponse(LocalDate date, long paidOrders, long ticketsSold, BigDecimal revenue) {
}
