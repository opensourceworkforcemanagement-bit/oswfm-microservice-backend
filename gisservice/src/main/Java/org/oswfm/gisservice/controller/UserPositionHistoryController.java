package org.oswfm.gisservice.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.oswfm.gisservice.dto.TripDTO;
import org.oswfm.gisservice.dto.UserPositionHistoryDTO;
import org.oswfm.gisservice.service.TripSegmentationService;
import org.oswfm.gisservice.service.UserPositionHistoryService;
import org.oswfm.gisservice.service.UserPositionHistoryService.Window;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/user-position-history")
@RequiredArgsConstructor
@Tag(name = "User Position History", description = "Historical position queries across configurable retention windows")
public class UserPositionHistoryController {

    private final UserPositionHistoryService service;
    private final TripSegmentationService tripSegmentationService;

    @GetMapping("/{window}/since")
    @Operation(summary = "Get all positions in the given window recorded after a timestamp",
               description = "window values: 7, 31, 61, 91, 180, 366")
    public ResponseEntity<List<UserPositionHistoryDTO>> getSince(
            @PathVariable int window,
            @Parameter(description = "ISO-8601 timestamp, e.g. 2025-01-01T00:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since) {
        return ResponseEntity.ok(service.getSince(resolveWindow(window), since));
    }

    @GetMapping("/{window}/user/{userId}")
    @Operation(summary = "Get full history for a user in the given window",
               description = "window values: 7, 31, 61, 91, 180, 366")
    public ResponseEntity<List<UserPositionHistoryDTO>> getByUserId(
            @PathVariable int window,
            @PathVariable Integer userId) {
        return ResponseEntity.ok(service.getByUserId(resolveWindow(window), userId));
    }

    @GetMapping("/{window}/user/{userId}/range")
    @Operation(summary = "Get history for a user in the given window between two timestamps",
               description = "window values: 7, 31, 61, 91, 180, 366")
    public ResponseEntity<List<UserPositionHistoryDTO>> getByUserIdInRange(
            @PathVariable int window,
            @PathVariable Integer userId,
            @Parameter(description = "ISO-8601 start timestamp")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "ISO-8601 end timestamp")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(service.getByUserIdInRange(resolveWindow(window), userId, from, to));
    }

    private Window resolveWindow(int days) {
        return switch (days) {
            case 7   -> Window.DAYS_7;
            case 31  -> Window.DAYS_31;
            case 61  -> Window.DAYS_61;
            case 91  -> Window.DAYS_91;
            case 180 -> Window.DAYS_180;
            case 366 -> Window.DAYS_366;
            default  -> throw new IllegalArgumentException(
                    "Invalid window: " + days + ". Valid values: 7, 31, 61, 91, 180, 366");
        };
    }

    @GetMapping("/{window}/user/{userId}/segment-trips")
    @Operation(summary = "Segment a list of chronological points into trips based on time gaps and movement thresholds",
               description = "window values: 7, 31, 61, 91, 180, 366")
    public List<List<UserPositionHistoryDTO>> segmentRawPointsIntoTrips(
            @PathVariable int window,
            @PathVariable Integer userId) {
        List<UserPositionHistoryDTO> chronologicalPoints = service.getByUserIdChronological(resolveWindow(window), userId);
        return tripSegmentationService.segmentRawPointsIntoTrips(chronologicalPoints);
    }

    @GetMapping("/{window}/user/{userId}/segment-trips-by-start-time")
    @Operation(summary = "Segment a list of chronological points into trips, each tagged with its start time",
               description = "window values: 7, 31, 61, 91, 180, 366")
    public List<TripDTO> segmentRawPointsIntoTripsByStartTime(
            @PathVariable int window,
            @PathVariable Integer userId) {
        List<UserPositionHistoryDTO> chronologicalPoints = service.getByUserIdChronological(resolveWindow(window), userId);
        return tripSegmentationService.buildTripsFromLocationStays(chronologicalPoints);
    }
}
