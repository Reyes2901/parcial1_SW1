package com.workflow.bpm.user.dto;

import com.workflow.bpm.user.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String role;
    private String departmentId;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .departmentId(user.getDepartmentId())
                .build();
    }
}
