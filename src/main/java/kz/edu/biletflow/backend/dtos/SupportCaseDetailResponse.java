package kz.edu.biletflow.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

// A case with its whole thread. Clients poll this; there are no live updates.
@Data
@AllArgsConstructor
public class SupportCaseDetailResponse {
    private SupportCaseResponse supportCase;
    private List<SupportMessageResponse> messages;
}
