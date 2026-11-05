package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.User;
import lombok.Data;

@Data
public class ChangeRoleRequest {
    @NotNull(message = "Role is required")
    private User.Role role;
}
