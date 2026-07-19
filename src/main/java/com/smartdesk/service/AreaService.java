package com.smartdesk.service;

import com.smartdesk.model.dto.AreaDTO;
import com.smartdesk.model.entity.Area;
import com.smartdesk.repository.AreaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AreaService {

    private final AreaRepository areaRepository;

    public AreaService(AreaRepository areaRepository) {
        this.areaRepository = areaRepository;
    }

    public List<AreaDTO> getAllAreas() {
        return areaRepository.findAll().stream()
                .filter(Area::getActive)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<AreaDTO> getActiveAreas() {
        return areaRepository.findAll().stream()
                .filter(Area::getActive)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AreaDTO getAreaById(UUID id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Área no encontrada"));
        return mapToDTO(area);
    }

    @Transactional
    public AreaDTO createArea(AreaDTO dto) {
        // Validate unique name
        if (areaRepository.findByName(dto.getName()).isPresent()) {
            throw new RuntimeException("Ya existe un área con el nombre '" + dto.getName() + "'");
        }

        Area area = new Area();
        area.setName(dto.getName());
        area.setDescription(dto.getDescription());
        area.setActive(true);
        area = areaRepository.save(area);
        return mapToDTO(area);
    }

    @Transactional
    public AreaDTO updateArea(UUID id, AreaDTO dto) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Área no encontrada"));

        // Validate unique name (excluding current)
        areaRepository.findByName(dto.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new RuntimeException("Ya existe un área con el nombre '" + dto.getName() + "'");
            }
        });

        area.setName(dto.getName());
        area.setDescription(dto.getDescription());
        if (dto.getActive() != null) {
            area.setActive(dto.getActive());
        }

        area = areaRepository.save(area);
        return mapToDTO(area);
    }

    @Transactional
    public void deactivateArea(UUID id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Área no encontrada"));
        area.setActive(false);
        areaRepository.save(area);
    }

    @Transactional
    public void deleteArea(UUID id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Área no encontrada"));
        area.setActive(false);
        areaRepository.save(area);
    }

    private AreaDTO mapToDTO(Area area) {
        AreaDTO dto = new AreaDTO();
        dto.setId(area.getId());
        dto.setName(area.getName());
        dto.setDescription(area.getDescription());
        dto.setActive(area.getActive());
        return dto;
    }
}
