package org.oswfm.gisservice.controller;

import java.util.List;

import org.oswfm.gisservice.dto.UserCurrentPositionDTO;
import org.oswfm.gisservice.service.UserCurrentPositionService;
import org.oswfm.commons.model.common.dto.response.CustomResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/user-current-positions")
@RequiredArgsConstructor
@Tag(name = "User Current Positions", description = "Live single-row-per-user position APIs")
public class UserCurrentPositionController {

    private final UserCurrentPositionService service;

    @GetMapping
    @Operation(summary = "Get current position for all users")
    public ResponseEntity<List<UserCurrentPositionDTO>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/latest")
    @Operation(summary = "Get current position for all users (alias for getAll)")
    public ResponseEntity<List<UserCurrentPositionDTO>> getLatest() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get current position for a specific user")
    public ResponseEntity<UserCurrentPositionDTO> getByUserId(@PathVariable Integer userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get all users currently within a given distance (meters) from a point")
    public ResponseEntity<List<UserCurrentPositionDTO>> findNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double distanceMeters) {
        return ResponseEntity.ok(service.findNearby(lat, lon, distanceMeters));
    }

    @PostMapping
    @Operation(summary = "Upsert the current position for a user (insert or update)")
    public ResponseEntity<CustomResponse<UserCurrentPositionDTO>> upsert(@Valid @RequestBody UserCurrentPositionDTO dto) {
        return ResponseEntity.ok(service.upsert(dto));
    }

    @PostMapping("/batch")
    @Operation(summary = "Upsert current positions for multiple users in a single request")
    public ResponseEntity<List<CustomResponse<UserCurrentPositionDTO>>> upsertBatch(@Valid @RequestBody List<UserCurrentPositionDTO> dtos) {
        return ResponseEntity.ok(service.upsertBatch(dtos));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete the current position record for a user")
    public ResponseEntity<Void> delete(@PathVariable Integer userId) {
        service.deleteByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}
