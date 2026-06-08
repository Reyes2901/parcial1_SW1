package com.workflow.bpm.document.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstracción del acceso físico a archivos para el Repositorio Documental.
 * <p>
 * Desacopla el almacenamiento concreto (disco local hoy) de la lógica de
 * negocio, dejando preparada una futura implementación de AWS S3 sin tocar
 * {@code DocumentRepositoryService}. El identificador {@code fileId} es la URL
 * pública del archivo (p.ej. {@code /api/files/general/{uuid}_{nombre}}).
 */
public interface FileStorageProvider {

    /** Indica si el archivo referenciado existe y es legible. */
    boolean exists(String fileId);

    /** Abre un stream de lectura del archivo (el llamador debe cerrarlo). */
    InputStream read(String fileId) throws IOException;

    /** Tamaño en bytes, o -1 si no se puede determinar. */
    long size(String fileId);

    /** MIME type detectado, o {@code application/octet-stream} por defecto. */
    String contentType(String fileId);
}
