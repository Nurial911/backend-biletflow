package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PayoutAccountResponse {
    private String bankName;
    private String accountHolder;
    private String ibanLast4;
    private boolean simulated;
    private LocalDateTime createdAt;
}
