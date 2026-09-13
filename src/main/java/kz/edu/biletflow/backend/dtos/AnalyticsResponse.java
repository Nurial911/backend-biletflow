package kz.edu.biletflow.backend.dtos;

import lombok.Data;

@Data
public class AnalyticsResponse {
    private long capacity;
    private long ticketsIssued;
    private long checkedIn;
    private long cancelledOrRefunded;
    private java.math.BigDecimal sales;
}
