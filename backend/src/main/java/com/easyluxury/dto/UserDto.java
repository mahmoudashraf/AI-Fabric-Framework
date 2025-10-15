package com.easyluxury.dto;

import com.easyluxury.entity.User.UserRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private UserRole role;
    private String supabaseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
