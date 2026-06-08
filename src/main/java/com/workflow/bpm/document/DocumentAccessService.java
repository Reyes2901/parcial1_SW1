package com.workflow.bpm.document;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validación centralizada de acceso documental.
 * <p>
 * Identidad = username (igual que el resto del backend). Los roles son un único
 * String por usuario expuesto como autoridad {@code ROLE_<role>}.
 */
@Service
public class DocumentAccessService {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    /**
     * Permite lectura si el usuario es propietario, está en allowedUsers,
     * comparte algún rol de allowedRoles, o es ADMIN.
     */
    public boolean canRead(DocumentRepositoryEntry entry, UserDetails user) {
        if (entry == null || user == null) {
            return false;
        }
        String username = user.getUsername();

        if (username.equals(entry.getUploadedBy())) {
            return true;
        }
        if (isAdmin(user)) {
            return true;
        }
        List<String> allowedUsers = entry.getAllowedUsers();
        if (allowedUsers != null && allowedUsers.contains(username)) {
            return true;
        }
        List<String> allowedRoles = entry.getAllowedRoles();
        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            for (GrantedAuthority authority : user.getAuthorities()) {
                // Comparación robusta: normaliza ambos lados quitando el prefijo
                // ROLE_, de modo que coincida tanto si allowedRoles guarda
                // "SUPERVISOR" como "ROLE_SUPERVISOR".
                String userRole = stripPrefix(authority.getAuthority());
                for (String allowed : allowedRoles) {
                    if (userRole.equals(stripPrefix(allowed))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Permite administrar (otorgar permisos / borrar) sólo al propietario o ADMIN.
     */
    public boolean canManage(DocumentRepositoryEntry entry, UserDetails user) {
        if (entry == null || user == null) {
            return false;
        }
        return user.getUsername().equals(entry.getUploadedBy()) || isAdmin(user);
    }

    private boolean isAdmin(UserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    private String stripPrefix(String authority) {
        return authority != null && authority.startsWith(ROLE_PREFIX)
                ? authority.substring(ROLE_PREFIX.length())
                : authority;
    }
}
