package org.oswfm.gisservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.oswfm.ResourceNotFoundException;
import org.oswfm.commons.model.common.dto.response.CustomResponse;
import org.oswfm.gisservice.buffer.GroupLocationBufferService;
import org.oswfm.gisservice.dto.UserCurrentPositionDTO;
import org.oswfm.gisservice.model.entity.UserCurrentPosition;
import org.oswfm.gisservice.repository.UserCurrentPositionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCurrentPositionService {

    private final UserCurrentPositionRepository repository;
    private final GroupLocationBufferService groupLocationBufferService;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional(readOnly = true)
    public List<UserCurrentPositionDTO> getAll() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserCurrentPositionDTO getByUserId(Integer userId) {
        return repository.findById(userId)
                .map(this::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("UserCurrentPosition", "userId", userId));
    }

    @Transactional(readOnly = true)
    public List<UserCurrentPositionDTO> findNearby(double lat, double lon, double distanceMeters) {
        return repository.findNearby(lat, lon, distanceMeters).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<CustomResponse<UserCurrentPositionDTO>> upsertBatch(List<UserCurrentPositionDTO> dtos) {
        return dtos.stream()
                .map(this::upsert)
                .collect(Collectors.toList());
    }

    /**
     * Upserts the current position for a user.
     * The DB trigger on user_current_position fans the write out to all history tables automatically.
     */
    @Transactional
    public CustomResponse<UserCurrentPositionDTO> upsert(UserCurrentPositionDTO dto) {
        try {
            boolean isInsert = !repository.existsById(dto.getUserId());
            UserCurrentPosition entity = repository.findById(dto.getUserId())
                    .orElse(new UserCurrentPosition());
            entity.setUserId(dto.getUserId());
            entity.setLocation(geometryFactory.createPoint(
                    new Coordinate(dto.getLongitude(), dto.getLatitude())));
            entity.setTransportMode(dto.getTransportMode());
            UserCurrentPositionDTO saved = toDTO(repository.save(entity));
            groupLocationBufferService.record(saved);
            return CustomResponse.<UserCurrentPositionDTO>builder()
                    .httpStatus(isInsert ? HttpStatus.CREATED : HttpStatus.OK)
                    .isSuccess(true)
                    .response(saved)
                    .build();
        } catch (Exception e) {
            log.error("[UserCurrentPosition] Failed to upsert position for userId={}: {}", dto.getUserId(), e.getMessage(), e);
            return CustomResponse.<UserCurrentPositionDTO>builder()
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .isSuccess(false)
                    .build();
        }
    }

    @Transactional
    public void deleteByUserId(Integer userId) {
        if (!repository.existsById(userId)) {
            throw new ResourceNotFoundException("UserCurrentPosition", "userId", userId);
        }
        repository.deleteById(userId);
    }

    private UserCurrentPositionDTO toDTO(UserCurrentPosition e) {
        UserCurrentPositionDTO dto = new UserCurrentPositionDTO();
        dto.setUserId(e.getUserId());
        if (e.getLocation() != null) {
            dto.setLatitude(e.getLocation().getY());
            dto.setLongitude(e.getLocation().getX());
        }
        dto.setLastUpdate(e.getLastUpdate());
        dto.setTransportMode(e.getTransportMode());
        return dto;
    }
}
