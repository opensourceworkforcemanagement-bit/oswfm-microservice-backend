package org.oswfm.gisservice.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.oswfm.gisservice.dto.TripDTO;
import org.oswfm.gisservice.dto.UserPositionHistoryDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Loads the real seed data from src/main/resources/data.txt and runs it through
 * TripSegmentationService exactly as the /segment-trips-by-start-time endpoint would,
 * restricted to the most recent 7 days for user 9, to answer: how many trips come out?
 */
class TripSegmentationServiceDataTxtTest {

    private static final Path DATA_FILE = Path.of("src/main/resources/data.txt");
    private static final DateTimeFormatter RECORDED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xx");

    @Test
    void countTripsForUser9InSevenDayWindow() throws IOException {
        List<UserPositionHistoryDTO> allPoints = loadDataTxt();

        OffsetDateTime latest = allPoints.stream()
                .map(UserPositionHistoryDTO::getRecordedAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow();
        OffsetDateTime windowStart = latest.minusDays(7);

        List<UserPositionHistoryDTO> chronological = allPoints.stream()
                .filter(p -> p.getUserId() == 9)
                .filter(p -> !p.getRecordedAt().isBefore(windowStart))
                .sorted(Comparator.comparing(UserPositionHistoryDTO::getRecordedAt))
                .toList();

        System.out.println("Latest timestamp: " + latest);
        System.out.println("7-day window start: " + windowStart);
        System.out.println("Points for user 9 in window: " + chronological.size());

        TripSegmentationService service = new TripSegmentationService();
        List<TripDTO> trips = service.segmentRawPointsIntoTripDTOs(chronological);

        System.out.println("Trip count: " + trips.size());
        for (TripDTO trip : trips) {
            OffsetDateTime end = trip.getPoints().get(trip.getPoints().size() - 1).getRecordedAt();
            System.out.println(" - start=" + trip.getStartTime() + " end=" + end
                    + " points=" + trip.getPoints().size());
        }

        assertEquals(13, trips.size());
    }

    private List<UserPositionHistoryDTO> loadDataTxt() throws IOException {
        List<String> lines = Files.readAllLines(DATA_FILE);
        List<UserPositionHistoryDTO> points = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", 5);
            Integer userId = Integer.valueOf(cols[1].trim());
            String recordedAtRaw = cols[3].trim();
            OffsetDateTime recordedAt = OffsetDateTime.parse(recordedAtRaw, RECORDED_AT_FORMAT);

            UserPositionHistoryDTO dto = new UserPositionHistoryDTO();
            dto.setUserId(userId);
            dto.setRecordedAt(recordedAt);
            points.add(dto);
        }

        return points;
    }
}
