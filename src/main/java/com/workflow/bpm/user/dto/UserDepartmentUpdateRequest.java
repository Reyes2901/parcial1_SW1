package com.workflow.bpm.user.dto;

import lombok.Data;

@Data
public class UserDepartmentUpdateRequest {
    private String departmentId; // Puede ser un ID válido o null si se desvincula
}