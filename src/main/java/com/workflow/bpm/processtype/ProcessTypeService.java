package com.workflow.bpm.processtype;

import com.workflow.bpm.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessTypeService {

    private final ProcessTypeRepository processTypeRepository;

    public List<ProcessType> findAllActive() {
        return processTypeRepository.findByIsActiveTrue();
    }

    public ProcessType findById(String id) {
        return processTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProcessType not found: " + id));
    }

    public ProcessType create(ProcessType processType) {
        processType.setCreatedAt(Instant.now());
        processType.setActive(true);
        return processTypeRepository.save(processType);
    }

    public ProcessType update(String id, ProcessType req) {
        ProcessType existing = findById(id);
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        return processTypeRepository.save(existing);
    }

    public void softDelete(String id) {
        ProcessType pt = findById(id);
        pt.setActive(false);
        processTypeRepository.save(pt);
    }
}
