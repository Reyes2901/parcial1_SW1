package com.workflow.bpm.policy;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

    private final PolicyRepository repo;
    private final GraphValidator validator;

    /**
     * Crea una nueva política en estado DRAFT
     */
    public ProcessDefinition create(ProcessDefinition def, String userId) {
        // Validar nombre único
        if (repo.existsByName(def.getName())) {
            throw new ValidationException("Ya existe una política con el nombre: " + def.getName());
        }

        def.setCreatedBy(userId);
        def.setStatus(ProcessDefinition.STATUS_DRAFT);
        def.setVersion("1.0");
        def.setCreatedAt(Instant.now());
        def.setUpdatedAt(Instant.now());

        ProcessDefinition saved = repo.save(def);
        log.info("Política creada: {} por {}", saved.getName(), userId);
        return saved;
    }

    /**
     * Actualiza una política existente (solo el creador puede hacerlo)
     */
    public ProcessDefinition update(String id, ProcessDefinition updated, String userId) {
        ProcessDefinition existing = repo.findByIdAndCreatedBy(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada o no tienes permisos"));

        // Actualizar campos
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setLanes(updated.getLanes());
        existing.setNodes(updated.getNodes());
        existing.setTransitions(updated.getTransitions());
        existing.setMetadata(updated.getMetadata());
        if (updated.getDepartmentIds() != null) {
            existing.setDepartmentIds(updated.getDepartmentIds());
        }
        if (updated.getProcessTypeId() != null) {
            existing.setProcessTypeId(updated.getProcessTypeId());
        }
        existing.setUpdatedAt(Instant.now());

        ProcessDefinition saved = repo.save(existing);
        log.info("Política actualizada: {}", saved.getName());
        return saved;
    }

    /**
     * Publica una política (cambia estado a PUBLISHED/ACTIVE)
     */
    public ProcessDefinition publish(String id, String userId) {
        ProcessDefinition def = repo.findByIdAndCreatedBy(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada o no tienes permisos"));

        // Validar estructura del grafo antes de publicar
        validator.validate(def);

        def.setStatus(ProcessDefinition.STATUS_ACTIVE);
        def.setUpdatedAt(Instant.now());

        ProcessDefinition saved = repo.save(def);
        log.info("Política publicada: {}", saved.getName());
        return saved;
    }

    /**
     * Activa una política (alias de publish)
     */
    public ProcessDefinition activate(String id, String userId) {
        return publish(id, userId);
    }

    /**
     * Archiva una política
     */
    public ProcessDefinition archive(String id, String userId) {
        ProcessDefinition def = repo.findByIdAndCreatedBy(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada o no tienes permisos"));

        def.setStatus(ProcessDefinition.STATUS_ARCHIVED);
        def.setUpdatedAt(Instant.now());

        ProcessDefinition saved = repo.save(def);
        log.info("Política archivada: {}", saved.getName());
        return saved;
    }

    /**
     * Obtiene todas las políticas publicadas/activas
     */
    public List<ProcessDefinition> findPublished() {
        return repo.findByStatus(ProcessDefinition.STATUS_ACTIVE);
    }

    /**
     * Obtiene todas las políticas activas (alias)
     */
    public List<ProcessDefinition> findActive() {
        return repo.findByStatus(ProcessDefinition.STATUS_ACTIVE);
    }

    /**
     * Obtiene todas las políticas
     */
    public List<ProcessDefinition> findAll() {
        return repo.findAll();
    }

    /**
     * Obtiene políticas por creador
     */
    public List<ProcessDefinition> findByCreator(String userId) {
        return repo.findByCreatedBy(userId);
    }

    /**
     * Busca una política por ID
     */
    public ProcessDefinition findById(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada: " + id));
    }

    /**
     * Elimina una política (solo el creador, y solo si no está activa)
     */
    public void delete(String id, String userId) {
        ProcessDefinition def = repo.findByIdAndCreatedBy(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada o no tienes permisos"));

        if (ProcessDefinition.STATUS_ACTIVE.equals(def.getStatus())) {
            throw new ValidationException("No se puede eliminar una política activa. Archívela primero.");
        }

        repo.deleteById(id);
        log.info("Política eliminada: {}", def.getName());
    }

    /**
     * Valida una política sin guardarla
     */
    public void validate(String id) {
        ProcessDefinition def = findById(id);
        validator.validate(def);
    }

    // método save simple
    public ProcessDefinition save(ProcessDefinition def) {
        def.setUpdatedAt(Instant.now());
        return repo.save(def);
    }
}