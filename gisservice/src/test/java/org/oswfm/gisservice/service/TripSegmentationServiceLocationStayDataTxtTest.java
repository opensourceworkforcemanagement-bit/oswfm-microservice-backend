package org.oswfm.gisservice.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import org.oswfm.gisservice.dto.LocationStayDTO;
import org.oswfm.gisservice.dto.TripDTO;
import org.oswfm.gisservice.dto.UserPositionHistoryDTO;

/**
 * Loads the real seed data from src/main/resources/data.txt and runs it through
 * TripSegmentationService#groupPointsIntoLocationStays, restricted to the most recent
 * 7 days for user 9, to sanity-check how the raw points group into location stays.
 */
class TripSegmentationServiceLocationStayDataTxtTest {

    private static final Path DATA_FILE = Path.of("src/main/resources/data.txt");
    private static final DateTimeFormatter RECORDED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xx");
    private static final Pattern POINT_PATTERN =
            Pattern.compile("POINT \\(([-0-9.]+) ([-0-9.]+)\\)");

    private final TripSegmentationService service = new TripSegmentationService();

    @Test
    void groupPointsIntoLocationStaysForUser9InSevenDayWindow() throws IOException {
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

        System.out.println("Points for user 9 in window: " + chronological.size());

        List<LocationStayDTO> stays = service.groupPointsIntoLocationStays(chronological);

        System.out.println("Stay count: " + stays.size());
        for (LocationStayDTO stay : stays) {
            System.out.println(" - start=" + stay.getStartTime() + " end=" + stay.getEndTime()
                    + " points=" + stay.getPoints().size());
        }

        assertFalse(stays.isEmpty());

        int totalPointsAcrossStays = stays.stream().mapToInt(s -> s.getPoints().size()).sum();
        assertEquals(chronological.size(), totalPointsAcrossStays);

        for (LocationStayDTO stay : stays) {
            assertFalse(stay.getEndTime().isBefore(stay.getStartTime()));
        }
    }

    @Test
    void staysCoverEntirePointListInOrder() throws IOException {
        List<UserPositionHistoryDTO> allPoints = loadDataTxt();

        List<UserPositionHistoryDTO> chronological = allPoints.stream()
                .filter(p -> p.getUserId() == 9)
                .sorted(Comparator.comparing(UserPositionHistoryDTO::getRecordedAt))
                .toList();

        List<LocationStayDTO> stays = service.groupPointsIntoLocationStays(chronological);

        List<UserPositionHistoryDTO> flattened = new ArrayList<>();
        for (LocationStayDTO stay : stays) {
            flattened.addAll(stay.getPoints());
        }

        assertEquals(chronological, flattened);

        for (LocationStayDTO stay : stays) {
            assertTrue(stay.getStartTime().equals(stay.getPoints().get(0).getRecordedAt()));
            assertTrue(stay.getEndTime().equals(stay.getPoints().get(stay.getPoints().size() - 1).getRecordedAt()));
        }
    }

    @Test
    void buildTripsFromLocationStaysProducesOneFewerTripThanStays() throws IOException {
        List<UserPositionHistoryDTO> allPoints = loadDataTxt();

        List<UserPositionHistoryDTO> chronological = allPoints.stream()
                .filter(p -> p.getUserId() == 9)
                .sorted(Comparator.comparing(UserPositionHistoryDTO::getRecordedAt))
                .toList();

        List<LocationStayDTO> stays = service.groupPointsIntoLocationStays(chronological);
        List<TripDTO> trips = service.buildTripsFromLocationStays(chronological);

        System.out.println("Stay count: " + stays.size() + ", trip count: " + trips.size());

        assertEquals(Math.max(0, stays.size() - 1), trips.size());
    }

    @Test
    void buildTripsFromLocationStaysConnectsAdjacentStayBoundaries() throws IOException {
        List<UserPositionHistoryDTO> allPoints = loadDataTxt();

        List<UserPositionHistoryDTO> chronological = allPoints.stream()
                .filter(p -> p.getUserId() == 9)
                .sorted(Comparator.comparing(UserPositionHistoryDTO::getRecordedAt))
                .toList();

        List<LocationStayDTO> stays = service.groupPointsIntoLocationStays(chronological);
        List<TripDTO> trips = service.buildTripsFromLocationStays(chronological);

        for (int i = 0; i < trips.size(); i++) {
            TripDTO trip = trips.get(i);
            UserPositionHistoryDTO expectedDeparture = stays.get(i).getEndPosition();
            UserPositionHistoryDTO expectedArrival = stays.get(i + 1).getStartPosition();

            assertEquals(2, trip.getPoints().size());
            assertEquals(expectedDeparture, trip.getPoints().get(0));
            assertEquals(expectedArrival, trip.getPoints().get(1));
            assertEquals(expectedDeparture.getRecordedAt(), trip.getStartTime());
        }
    }

    @Test
    void buildTripsFromLocationStaysAreChronologicallyOrdered() throws IOException {
        List<UserPositionHistoryDTO> allPoints = loadDataTxt();

        List<UserPositionHistoryDTO> chronological = allPoints.stream()
                .filter(p -> p.getUserId() == 9)
                .sorted(Comparator.comparing(UserPositionHistoryDTO::getRecordedAt))
                .toList();

        List<TripDTO> trips = service.buildTripsFromLocationStays(chronological);

        System.out.println("Trip count: " + trips.size());
        for (TripDTO trip : trips) {
            System.out.println("   points: " + trip.getPoints().size());
            System.out.println("starttime: " + trip.getStartTime());
            System.out.println("endtime: " + trip.getEndTime());
        }

        for (int i = 1; i < trips.size(); i++) {
            assertFalse(trips.get(i).getStartTime().isBefore(trips.get(i - 1).getStartTime()));
        }
    }

    @Test
    void buildTripsFromLocationStaysReturnsEmptyForSingleStay() {
        UserPositionHistoryDTO onlyPoint = new UserPositionHistoryDTO();
        onlyPoint.setUserId(9);
        onlyPoint.setLatitude(34.0);
        onlyPoint.setLongitude(-84.0);
        onlyPoint.setRecordedAt(OffsetDateTime.now());

        List<TripDTO> trips = service.buildTripsFromLocationStays(List.of(onlyPoint));

        assertTrue(trips.isEmpty());
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
            String locationRaw = cols[2].trim();
            String recordedAtRaw = cols[3].trim();
            OffsetDateTime recordedAt = OffsetDateTime.parse(recordedAtRaw, RECORDED_AT_FORMAT);

            Matcher matcher = POINT_PATTERN.matcher(locationRaw);
            if (!matcher.find()) {
                continue;
            }
            // WKT POINT is (longitude latitude)
            double longitude = Double.parseDouble(matcher.group(1));
            double latitude = Double.parseDouble(matcher.group(2));

            UserPositionHistoryDTO dto = new UserPositionHistoryDTO();
            dto.setUserId(userId);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setRecordedAt(recordedAt);
            points.add(dto);
        }

        return points;
    }
}
