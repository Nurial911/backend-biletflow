package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.SimulatedPaymentOutcome;
import lombok.Data;

// Optional body of POST /api/orders/{id}/pay. Without it the simulated payment succeeds.
@Data
public class PayOrderRequest {
    private SimulatedPaymentOutcome simulatedOutcome;
}
