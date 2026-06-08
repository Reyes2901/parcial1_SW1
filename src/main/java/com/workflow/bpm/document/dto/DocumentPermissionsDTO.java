package com.workflow.bpm.document.dto;

import com.workflow.bpm.document.DocumentRepositoryEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Usuarios y roles autorizados sobre un documento.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPermissionsDTO {

    private String entryId;
    private String owner;
    private List<String> allowedUsers;
    private List<String> allowedRoles;

    public static DocumentPermissionsDTO from(DocumentRepositoryEntry e) {
        return DocumentPermissionsDTO.builder()
                .entryId(e.getId())
                .owner(e.getUploadedBy())
                .allowedUsers(e.getAllowedUsers() != null ? e.getAllowedUsers() : new ArrayList<>())
                .allowedRoles(e.getAllowedRoles() != null ? e.getAllowedRoles() : new ArrayList<>())
                .build();
    }
}
