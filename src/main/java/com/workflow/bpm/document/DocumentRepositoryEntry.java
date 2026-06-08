package com.workflow.bpm.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Referencia lógica de un archivo dentro del Repositorio Documental Empresarial.
 * <p>
 * NO duplica el binario: {@code fileId} apunta a un archivo ya existente
 * (la URL pública devuelta por el upload actual, p.ej.
 * {@code /api/files/general/{uuid}_{nombre}}). Esta entidad sólo agrega la
 * capa de organización (por trámite/instancia y por política), control de
 * acceso y trazabilidad documental.
 */
@Document(collection = "document_repository_entries")
@CompoundIndex(name = "instance_deleted", def = "{'processInstanceId': 1, 'deleted': 1}")
@CompoundIndex(name = "policy_deleted", def = "{'policyId': 1, 'deleted': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRepositoryEntry {

    @Id
    private String id;

    /** Referencia lógica al archivo físico existente (URL del upload actual). */
    private String fileId;

    /** Instancia de proceso (trámite) a la que pertenece el documento. */
    @Indexed
    private String processInstanceId;

    /** Política (ProcessDefinition.id) dueña del trámite. */
    @Indexed
    private String policyId;

    /** Tarea que originó/aportó el documento (opcional). */
    @Indexed
    private String taskId;

    /** Usuario propietario (username del que lo subió/adjuntó). */
    @Indexed
    private String uploadedBy;

    private Instant uploadedAt;

    /** Clasificación detectada: PDF, WORD, EXCEL, IMAGE, SIGNATURE, UNKNOWN. */
    private String documentType;

    /** Nombre original del archivo (sin el prefijo UUID interno), si se puede derivar. */
    private String originalFilename;

    /** MIME type detectado del archivo físico. */
    private String mimeType;

    /** Tamaño en bytes del archivo físico, o null/-1 si no se pudo determinar. */
    private Long fileSize;

    /** Marca si el documento es obligatorio para el trámite. */
    private boolean required;

    /** Lista blanca de usuarios (usernames) autorizados además del propietario. */
    @Builder.Default
    private List<String> allowedUsers = new ArrayList<>();

    /** Lista blanca de roles autorizados (valores de User.role, sin prefijo ROLE_). */
    @Builder.Default
    private List<String> allowedRoles = new ArrayList<>();

    /** Contador simple de versión lógica (no versionado binario). */
    @Builder.Default
    private int version = 1;

    /** Borrado lógico: nunca elimina el archivo físico. */
    @Builder.Default
    @Indexed
    private boolean deleted = false;

    @CreatedDate
    private Instant createdAt;
}
