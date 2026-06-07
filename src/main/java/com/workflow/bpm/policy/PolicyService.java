package com.workflow.bpm.policy;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import com.workflow.bpm.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

        // @Builder.Default does not apply to @NoArgsConstructor — initialize null
        // collections
        if (def.getLanes() == null)
            def.setLanes(new ArrayList<>());
        if (def.getNodes() == null)
            def.setNodes(new ArrayList<>());
        if (def.getTransitions() == null)
            def.setTransitions(new ArrayList<>());
        if (def.getDepartmentIds() == null)
            def.setDepartmentIds(new ArrayList<>());
        if (def.getMetadata() == null)
            def.setMetadata(new HashMap<>());

        def.setCreatedBy(userId);
        def.setStatus(ProcessDefinition.STATUS_DRAFT);
        def.setVersion("1.0");
        def.setCreatedAt(Instant.now());
        def.setUpdatedAt(Instant.now());

        syncLaneDepartmentsFromXml(def);

        try {
            ProcessDefinition saved = repo.save(def);
            log.info("Política creada: {} por {}", saved.getName(), userId);
            return saved;
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ValidationException("Ya existe una política con el nombre: " + def.getName());
        }
    }

    /**
     * Actualiza una política existente (Permite edición colaborativa de políticas
     * públicas)
     */
    public ProcessDefinition update(String id, ProcessDefinition updated, String userId) {
        // 🔄 CAMBIO AQUÍ: Buscamos solo por ID, ya no filtramos obligatoriamente por el
        // creador
        ProcessDefinition existing = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Política no encontrada con el ID: " + id));

        // Actualizar campos
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setLanes(updated.getLanes());
        existing.setNodes(updated.getNodes());
        existing.setTransitions(updated.getTransitions());
        existing.setMetadata(updated.getMetadata());

        // Defensa de concurrencia: el XML colaborativo es fuente de verdad del
        // canal WebSocket y no debe ser sobrescrito por PUT REST.
        String incomingXml = updated.getBpmnXml();
        if (incomingXml != null && !incomingXml.isBlank()) {
            log.warn("PUT policy '{}' ignoró bpmnXml entrante de '{}' para preservar XML colaborativo",
                    id, userId);
        }
        if (updated.getDepartmentIds() != null) {
            existing.setDepartmentIds(updated.getDepartmentIds());
        }
        if (updated.getProcessTypeId() != null) {
            existing.setProcessTypeId(updated.getProcessTypeId());
        }
        existing.setUpdatedAt(Instant.now());
        syncLaneDepartmentsFromXml(existing);

        ProcessDefinition saved = repo.save(existing);
        log.info("Política actualizada en Base de Datos: {}", saved.getName());
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
        syncLaneDepartmentsFromXml(def);
        return repo.save(def);
    }

    /**
     * Extrae custom:departmentId de los elementos <lane> en el BPMN XML
     * y actualiza Lane.departmentId antes de persistir.
     */
    private void syncLaneDepartmentsFromXml(ProcessDefinition def) {
        if (def.getBpmnXml() == null || def.getBpmnXml().isBlank())
            return;
        if (def.getLanes() == null || def.getLanes().isEmpty())
            return;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(
                    new ByteArrayInputStream(def.getBpmnXml().getBytes(StandardCharsets.UTF_8)));
            NodeList laneElements = doc.getElementsByTagNameNS("*", "lane");
            for (int i = 0; i < laneElements.getLength(); i++) {
                Element laneEl = (Element) laneElements.item(i);
                String laneId = laneEl.getAttribute("id");
                String deptId = null;
                NamedNodeMap attrs = laneEl.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Attr attr = (Attr) attrs.item(j);
                    if ("departmentId".equals(attr.getLocalName())) {
                        deptId = attr.getValue();
                        break;
                    }
                }
                if (deptId != null && !deptId.isBlank()) {
                    final String finalDeptId = deptId;
                    def.getLanes().stream()
                            .filter(l -> laneId.equals(l.getId()))
                            .findFirst()
                            .ifPresent(l -> l.setDepartmentId(finalDeptId));
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse BPMN XML for lane department sync: {}", e.getMessage());
        }
    }
}