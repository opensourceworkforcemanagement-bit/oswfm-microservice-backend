package org.oswfm.gisservice.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import org.oswfm.gisservice.dto.LocationStayDTO;
import org.oswfm.gisservice.dto.TripDTO;
import org.oswfm.gisservice.dto.UserPositionHistoryDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripSegmentationService {

    // 5 minutes in milliseconds
    private static final long IDLE_TIMEOUT_MS = 5 * 60 * 1000;
    // Minimum distance (in meters) to consider points as "moving"
    private static final double MOVEMENT_THRESHOLD_METERS = 15.0;

    /**
     * Splits on time gaps alone: any gap longer than {@link #IDLE_TIMEOUT_MS} between
     * consecutive recorded points ends the current trip, regardless of how far apart
     * the points on either side of the gap are.
     */
    public List<List<UserPositionHistoryDTO>> segmentRawPointsIntoTrips(List<UserPositionHistoryDTO> chronologicalPoints) {
        List<List<UserPositionHistoryDTO>> isolatedTrips = new ArrayList<>();
        if (chronologicalPoints == null || chronologicalPoints.isEmpty()) {
            return isolatedTrips;
        }

        List<UserPositionHistoryDTO> currentTrip = new ArrayList<>();
        currentTrip.add(chronologicalPoints.get(0));

        for (int i = 1; i < chronologicalPoints.size(); i++) {
            UserPositionHistoryDTO previousPoint = chronologicalPoints.get(i - 1);
            UserPositionHistoryDTO currentPoint = chronologicalPoints.get(i);

            long timeGap = toEpochMillis(currentPoint.getRecordedAt()) - toEpochMillis(previousPoint.getRecordedAt());

            if (timeGap > IDLE_TIMEOUT_MS) {
                // Close the current trip if it has valid data
                if (currentTrip.size() > 1) {
                    isolatedTrips.add(new ArrayList<>(currentTrip));
                }
                // Start a brand new trip allocation
                currentTrip.clear();
            }

            currentTrip.add(currentPoint);
        }

        // Add the trailing final trip segment if valid
        if (currentTrip.size() > 1) {
            isolatedTrips.add(currentTrip);
        }

        return isolatedTrips;
    }

    /**
     * Splits on time gaps like {@link #segmentRawPointsIntoTrips}, and additionally ends a trip
     * when a sustained run of continuously-recorded points (each less than {@link #IDLE_TIMEOUT_MS}
     * apart) spans at least {@link #IDLE_TIMEOUT_MS} in total while covering less than
     * {@link #MOVEMENT_THRESHOLD_METERS} of movement from where that run started — i.e. the person
     * kept pinging steadily but was essentially stationary (GPS drift while idle).
     */
    public List<List<UserPositionHistoryDTO>> segmentRawPointsIntoTripsWithIdleDetection(List<UserPositionHistoryDTO> chronologicalPoints) {
        List<List<UserPositionHistoryDTO>> isolatedTrips = new ArrayList<>();
        if (chronologicalPoints == null || chronologicalPoints.isEmpty()) {
            return isolatedTrips;
        }

        List<UserPositionHistoryDTO> currentTrip = new ArrayList<>();
        currentTrip.add(chronologicalPoints.get(0));
        int idleWindowStart = 0;

        for (int i = 1; i < chronologicalPoints.size(); i++) {
            UserPositionHistoryDTO previousPoint = chronologicalPoints.get(i - 1);
            UserPositionHistoryDTO currentPoint = chronologicalPoints.get(i);

            long timeGap = toEpochMillis(currentPoint.getRecordedAt()) - toEpochMillis(previousPoint.getRecordedAt());
            boolean tripEnded = timeGap > IDLE_TIMEOUT_MS;

            if (!tripEnded) {
                UserPositionHistoryDTO idleWindowStartPoint = chronologicalPoints.get(idleWindowStart);
                long idleWindowSpan = toEpochMillis(currentPoint.getRecordedAt()) - toEpochMillis(idleWindowStartPoint.getRecordedAt());
                double distanceFromWindowStart = calculateHaversineDistance(idleWindowStartPoint, currentPoint);

                if (idleWindowSpan >= IDLE_TIMEOUT_MS && distanceFromWindowStart < MOVEMENT_THRESHOLD_METERS) {
                    tripEnded = true;
                }
            }

            if (tripEnded) {
                // Close the current trip if it has valid data
                if (currentTrip.size() > 1) {
                    isolatedTrips.add(new ArrayList<>(currentTrip));
                }
                // Start a brand new trip allocation
                currentTrip.clear();
                idleWindowStart = i;
            }

            currentTrip.add(currentPoint);
        }

        // Add the trailing final trip segment if valid
        if (currentTrip.size() > 1) {
            isolatedTrips.add(currentTrip);
        }

        return isolatedTrips;
    }

    public TreeMap<OffsetDateTime, List<UserPositionHistoryDTO>> segmentRawPointsIntoTripsByStartTime(List<UserPositionHistoryDTO> chronologicalPoints) {
        TreeMap<OffsetDateTime, List<UserPositionHistoryDTO>> tripsByStartTime = new TreeMap<>();

        for (List<UserPositionHistoryDTO> trip : segmentRawPointsIntoTrips(chronologicalPoints)) {
            OffsetDateTime startTime = trip.get(0).getRecordedAt();
            tripsByStartTime.put(startTime, trip);
        }

        return tripsByStartTime;
    }

    public List<TripDTO> segmentRawPointsIntoTripDTOs(List<UserPositionHistoryDTO> chronologicalPoints) {
        List<TripDTO> trips = new ArrayList<>();

        for (List<UserPositionHistoryDTO> trip : segmentRawPointsIntoTrips(chronologicalPoints)) {
            trips.add(new TripDTO(trip.get(0).getRecordedAt(), trip));
        }

        return trips;
    }

    /**
     * Groups chronologically-ordered points into stays: runs of consecutive points that stay
     * within {@link #MOVEMENT_THRESHOLD_METERS} of the point where the stay started. Each point
     * is compared against the anchor (first point) of the current stay, so slow drift across many
     * points doesn't accumulate into a false "moved" reading the way point-to-point comparison would.
     */
    public List<LocationStayDTO> groupPointsIntoLocationStays(List<UserPositionHistoryDTO> chronologicalPoints) {
        List<LocationStayDTO> stays = new ArrayList<>();
        if (chronologicalPoints == null || chronologicalPoints.isEmpty()) {
            return stays;
        }

        List<UserPositionHistoryDTO> currentStay = new ArrayList<>();
        currentStay.add(chronologicalPoints.get(0));
        UserPositionHistoryDTO stayAnchor = chronologicalPoints.get(0);

        for (int i = 1; i < chronologicalPoints.size(); i++) {
            UserPositionHistoryDTO currentPoint = chronologicalPoints.get(i);
            double distanceFromAnchor = calculateHaversineDistance(stayAnchor, currentPoint);

            if (distanceFromAnchor >= MOVEMENT_THRESHOLD_METERS) {
                stays.add(toLocationStayDTO(currentStay));
                currentStay = new ArrayList<>();
                stayAnchor = currentPoint;
            }

            currentStay.add(currentPoint);
        }

        stays.add(toLocationStayDTO(currentStay));

        return stays;
    }

    /**
     * Total time spent at each location stay, keyed by the {@link LocationStayDTO} itself,
     * built from {@link #groupPointsIntoLocationStays}.
     */
    public TreeMap<LocationStayDTO, Duration> calculateDurationAtEachLocation(List<UserPositionHistoryDTO> chronologicalPoints) {
        TreeMap<LocationStayDTO, Duration> durationsByStay = new TreeMap<>(
                (a, b) -> a.getStartTime().compareTo(b.getStartTime()));

        for (LocationStayDTO stay : groupPointsIntoLocationStays(chronologicalPoints)) {
            durationsByStay.put(stay, stay.getDuration());
        }

        return durationsByStay;
    }

    /**
     * Builds trips from the movement between consecutive {@link #groupPointsIntoLocationStays}
     * stays: each trip runs from the last point of one stay to the first point of the next,
     * so a stay's boundary point is shared with the trip on either side of it (mirroring how
     * {@link #segmentRawPointsIntoTrips} shares points across a time-gap boundary).
     */
    public List<TripDTO> buildTripsFromLocationStays(List<UserPositionHistoryDTO> chronologicalPoints) {
        List<TripDTO> trips = new ArrayList<>();
        List<LocationStayDTO> stays = groupPointsIntoLocationStays(chronologicalPoints);

        List<UserPositionHistoryDTO> tripPoints = new ArrayList<>();

        for (int i = 0; i < stays.size() - 1; i++) {

            LocationStayDTO currentStay = stays.get(i);
            UserPositionHistoryDTO departurePoint = currentStay.getEndPosition();
            LocationStayDTO nextStay = stays.get(i + 1);
            UserPositionHistoryDTO arrivalPoint = nextStay.getStartPosition();

            if( Duration.between(departurePoint.getRecordedAt(), arrivalPoint.getRecordedAt()).toMillis() < IDLE_TIMEOUT_MS) {
                tripPoints.add(departurePoint);
                tripPoints.add(arrivalPoint);
            }
            else {
                // If the gap between the departure and arrival points is too long, we consider it a new trip
                if (!tripPoints.isEmpty()) {
                    trips.add(new TripDTO(departurePoint.getRecordedAt(), tripPoints));
                    tripPoints = new ArrayList<>();
                    continue; // Skip adding the arrival point to the current trip
                }
            }

            //
            if(nextStay.getDuration().toMillis() > IDLE_TIMEOUT_MS) {
                trips.add(new TripDTO(departurePoint.getRecordedAt(), tripPoints));
                tripPoints = new ArrayList<>();
            }

        }

        return trips;
    }

    private LocationStayDTO toLocationStayDTO(List<UserPositionHistoryDTO> stayPoints) {
        OffsetDateTime startTime = stayPoints.get(0).getRecordedAt();
        OffsetDateTime endTime = stayPoints.get(stayPoints.size() - 1).getRecordedAt();
        return new LocationStayDTO(startTime, endTime, stayPoints);
    }

    private long toEpochMillis(OffsetDateTime timestamp) {
        return timestamp.toInstant().toEpochMilli();
    }

    // Haversine formula to compute distance between two coordinates in meters
    private double calculateHaversineDistance(UserPositionHistoryDTO p1, UserPositionHistoryDTO p2) {
        double EarthRadius = 6371000; // Meters
        double dLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double dLng = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(p1.getLatitude())) * Math.cos(Math.toRadians(p2.getLatitude())) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EarthRadius * c;
    }
}
