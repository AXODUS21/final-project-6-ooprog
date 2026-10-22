package plano.service;

import plano.model.*;
import plano.repository.DataStore;
import plano.repository.RegistrationRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service generating system-wide reports and analytics for Administrators
 * and scoped event performance metrics for Organizers.
 * Registrations of cancelled events are left out of all totals, and the
 * attendance rate only counts events that have been completed.
 *
 * The report classes below are read-only for every other class: their fields are private
 * and have getters only. ReportService fills them in while building a report, which Java
 * allows because an enclosing class can use the private members of its nested classes.
 */
public class ReportService {
    private final DataStore dataStore;

    public ReportService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    // ==================== ADMIN SYSTEM-WIDE ANALYTICS ====================

    public AdminDashboardData getAdminDashboard() {
        AdminDashboardData data = new AdminDashboardData();
        LocalDateTime now = LocalDateTime.now();
        RegistrationRepository regRepo = dataStore.getRegistrationRepository();
        List<Event> allEvents = dataStore.getEventRepository().findAll();
        List<User> allUsers = dataStore.getUserRepository().findAll();

        Set<String> cancelledEventIds = new HashSet<>();
        for (Event e : allEvents) {
            if (e.isCancelled()) {
                cancelledEventIds.add(e.getEventId());
            }
        }

        data.totalEvents = allEvents.size();
        data.upcomingEvents = (int) allEvents.stream().filter(e -> e.isUpcoming() && !e.hasEnded(now)).count();
        data.ongoingEvents = (int) allEvents.stream().filter(e -> e.isOngoing() && !e.hasEnded(now)).count();
        data.awaitingCompletionEvents = (int) allEvents.stream().filter(e -> e.isAwaitingCompletion(now)).count();
        data.completedEvents = (int) allEvents.stream().filter(Event::isCompleted).count();
        data.cancelledEvents = cancelledEventIds.size();

        data.totalUsers = allUsers.size();
        data.totalOrganizers = (int) allUsers.stream().filter(User::isOrganizer).count();
        data.totalAttendees = (int) allUsers.stream().filter(User::isAttendee).count();

        List<Registration> liveRegs = regRepo.findAll().stream()
                .filter(r -> !cancelledEventIds.contains(r.getEventId()))
                .collect(Collectors.toList());
        data.totalRegistrations = (int) liveRegs.stream().filter(r -> !r.isCancelled()).count();
        data.confirmedRegistrations = (int) liveRegs.stream().filter(Registration::isConfirmed).count();
        data.waitlistedRegistrations = (int) liveRegs.stream().filter(Registration::isWaitlisted).count();
        data.checkedInAttendees = (int) liveRegs.stream().filter(Registration::isCheckedIn).count();

        // Attendance (checked-in vs. seats taken) over completed events only
        for (Event e : allEvents) {
            if (e.isCompleted()) {
                data.completedEventRegistrations += regRepo.countSeatsTakenByEvent(e.getEventId());
                data.completedEventCheckIns += regRepo.countCheckedInByEvent(e.getEventId());
            }
        }

        // Category Popularity: seats taken per category, cancelled events excluded
        Map<String, Integer> catCounts = new LinkedHashMap<>();
        for (Event e : allEvents) {
            if (e.isCancelled()) {
                continue;
            }
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            int count = regRepo.countSeatsTakenByEvent(e.getEventId());
            catCounts.put(cat, catCounts.getOrDefault(cat, 0) + count);
        }
        data.categoryPopularity = Collections.unmodifiableMap(catCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new)));

        // Organizer Activity
        List<OrganizerStats> orgList = new ArrayList<>();
        for (User u : allUsers) {
            if (u.isOrganizer() || u.isAdmin()) {
                List<Event> orgEvents = dataStore.getEventRepository().findByOrganizer(u.getUserId());
                if (!orgEvents.isEmpty() || u.isOrganizer()) {
                    int eventCount = orgEvents.size();
                    int totalRegistrations = 0;
                    int totalCheckedIn = 0;
                    for (Event ev : orgEvents) {
                        if (ev.isCancelled()) {
                            continue;
                        }
                        totalRegistrations += regRepo.countSeatsTakenByEvent(ev.getEventId());
                        totalCheckedIn += regRepo.countCheckedInByEvent(ev.getEventId());
                    }
                    orgList.add(new OrganizerStats(u.getUserId(), u.getFullName(), u.getUsername(), eventCount, totalRegistrations, totalCheckedIn));
                }
            }
        }
        orgList.sort(Comparator.comparingInt(OrganizerStats::getEventCount).reversed());
        data.organizerStats = Collections.unmodifiableList(orgList);

        return data;
    }

    // ==================== ORGANIZER ANALYTICS ====================

    public OrganizerDashboardData getOrganizerDashboard(String organizerId) {
        OrganizerDashboardData data = new OrganizerDashboardData();
        LocalDateTime now = LocalDateTime.now();
        RegistrationRepository regRepo = dataStore.getRegistrationRepository();
        List<Event> orgEvents = dataStore.getEventRepository().findByOrganizer(organizerId);

        data.totalEvents = orgEvents.size();
        data.upcomingEvents = (int) orgEvents.stream().filter(e -> e.isUpcoming() && !e.hasEnded(now)).count();
        data.ongoingEvents = (int) orgEvents.stream().filter(e -> e.isOngoing() && !e.hasEnded(now)).count();
        data.awaitingCompletionEvents = (int) orgEvents.stream().filter(e -> e.isAwaitingCompletion(now)).count();
        data.completedEvents = (int) orgEvents.stream().filter(Event::isCompleted).count();
        data.cancelledEvents = (int) orgEvents.stream().filter(Event::isCancelled).count();

        List<EventPerformance> performanceList = new ArrayList<>();
        for (Event ev : orgEvents) {
            int seatsTaken = regRepo.countSeatsTakenByEvent(ev.getEventId());
            int waitlisted = regRepo.countWaitlistedByEvent(ev.getEventId());
            int checkedIn = regRepo.countCheckedInByEvent(ev.getEventId());
            double avgRating = dataStore.getFeedbackRepository().getAverageRatingForEvent(ev.getEventId());
            int reviewCount = dataStore.getFeedbackRepository().findByEvent(ev.getEventId()).size();

            if (!ev.isCancelled()) {
                data.totalRegistrations += seatsTaken;
                data.totalWaitlisted += waitlisted;
                data.totalCheckedIn += checkedIn;
            }

            performanceList.add(new EventPerformance(
                    ev.getEventId(),
                    ev.getTitle(),
                    ev.getStatus(),
                    ev.getCapacity(),
                    seatsTaken,
                    waitlisted,
                    checkedIn,
                    avgRating,
                    reviewCount
            ));
        }
        data.eventPerformances = Collections.unmodifiableList(performanceList);

        return data;
    }

    // ==================== REPORT CLASSES (read-only outside ReportService) ====================

    public static class AdminDashboardData {
        private int totalEvents;
        private int upcomingEvents;
        private int ongoingEvents;
        private int awaitingCompletionEvents;
        private int completedEvents;
        private int cancelledEvents;

        private int totalUsers;
        private int totalOrganizers;
        private int totalAttendees;

        private int totalRegistrations;
        private int confirmedRegistrations;
        private int waitlistedRegistrations;
        private int checkedInAttendees;

        private int completedEventRegistrations;
        private int completedEventCheckIns;

        private Map<String, Integer> categoryPopularity = Collections.emptyMap();
        private List<OrganizerStats> organizerStats = Collections.emptyList();

        private AdminDashboardData() {
        }

        public int getTotalEvents() { return totalEvents; }
        public int getUpcomingEvents() { return upcomingEvents; }
        public int getOngoingEvents() { return ongoingEvents; }
        /** Events whose end time has passed but that were never marked Completed. */
        public int getAwaitingCompletionEvents() { return awaitingCompletionEvents; }
        public int getCompletedEvents() { return completedEvents; }
        public int getCancelledEvents() { return cancelledEvents; }

        public int getTotalUsers() { return totalUsers; }
        public int getTotalOrganizers() { return totalOrganizers; }
        public int getTotalAttendees() { return totalAttendees; }

        /** Active registrations (confirmed + checked in + waitlisted) of events that are not cancelled. */
        public int getTotalRegistrations() { return totalRegistrations; }
        public int getConfirmedRegistrations() { return confirmedRegistrations; }
        public int getWaitlistedRegistrations() { return waitlistedRegistrations; }
        public int getCheckedInAttendees() { return checkedInAttendees; }

        /** Seats taken at completed events. */
        public int getCompletedEventRegistrations() { return completedEventRegistrations; }
        /** How many of the people holding those seats checked in. */
        public int getCompletedEventCheckIns() { return completedEventCheckIns; }

        /** Check-ins as a percentage of seats taken at completed events (0 when there are none). */
        public double getOverallAttendanceRate() {
            if (completedEventRegistrations <= 0) return 0.0;
            return ((double) completedEventCheckIns / completedEventRegistrations) * 100.0;
        }

        /** Seats taken per category, most popular first. The map cannot be changed. */
        public Map<String, Integer> getCategoryPopularity() { return categoryPopularity; }
        /** Organizers with the most events first. The list cannot be changed. */
        public List<OrganizerStats> getOrganizerStats() { return organizerStats; }
    }

    public static class OrganizerStats {
        private final String userId;
        private final String fullName;
        private final String username;
        private final int eventCount;
        private final int totalRegistrations;
        private final int totalCheckedIn;

        public OrganizerStats(String userId, String fullName, String username, int eventCount, int totalRegistrations, int totalCheckedIn) {
            this.userId = userId;
            this.fullName = fullName;
            this.username = username;
            this.eventCount = eventCount;
            this.totalRegistrations = totalRegistrations;
            this.totalCheckedIn = totalCheckedIn;
        }

        public String getUserId() { return userId; }
        public String getFullName() { return fullName; }
        public String getUsername() { return username; }
        public int getEventCount() { return eventCount; }
        public int getTotalRegistrations() { return totalRegistrations; }
        public int getTotalCheckedIn() { return totalCheckedIn; }
    }

    public static class OrganizerDashboardData {
        private int totalEvents;
        private int upcomingEvents;
        private int ongoingEvents;
        private int awaitingCompletionEvents;
        private int completedEvents;
        private int cancelledEvents;

        private int totalRegistrations;
        private int totalWaitlisted;
        private int totalCheckedIn;

        private List<EventPerformance> eventPerformances = Collections.emptyList();

        private OrganizerDashboardData() {
        }

        public int getTotalEvents() { return totalEvents; }
        public int getUpcomingEvents() { return upcomingEvents; }
        public int getOngoingEvents() { return ongoingEvents; }
        /** Events whose end time has passed but that were never marked Completed. */
        public int getAwaitingCompletionEvents() { return awaitingCompletionEvents; }
        public int getCompletedEvents() { return completedEvents; }
        public int getCancelledEvents() { return cancelledEvents; }

        /** Seats taken across the organizer's events that are not cancelled. */
        public int getTotalRegistrations() { return totalRegistrations; }
        public int getTotalWaitlisted() { return totalWaitlisted; }
        public int getTotalCheckedIn() { return totalCheckedIn; }

        /** One row per event. The list cannot be changed. */
        public List<EventPerformance> getEventPerformances() { return eventPerformances; }
    }

    public static class EventPerformance {
        private final String eventId;
        private final String title;
        private final EventStatus status;
        private final int capacity;
        private final int seatsTaken;
        private final int waitlisted;
        private final int checkedIn;
        private final double averageRating;
        private final int reviewCount;

        public EventPerformance(String eventId, String title, EventStatus status, int capacity,
                                int seatsTaken, int waitlisted, int checkedIn, double averageRating, int reviewCount) {
            this.eventId = eventId;
            this.title = title;
            this.status = status;
            this.capacity = capacity;
            this.seatsTaken = seatsTaken;
            this.waitlisted = waitlisted;
            this.checkedIn = checkedIn;
            this.averageRating = averageRating;
            this.reviewCount = reviewCount;
        }

        public String getEventId() { return eventId; }
        public String getTitle() { return title; }
        public EventStatus getStatus() { return status; }
        public int getCapacity() { return capacity; }
        /** Confirmed and checked-in attendees. */
        public int getSeatsTaken() { return seatsTaken; }
        public int getWaitlisted() { return waitlisted; }
        public int getCheckedIn() { return checkedIn; }
        public double getAverageRating() { return averageRating; }
        public int getReviewCount() { return reviewCount; }

        public double getFillRate() {
            if (capacity <= 0) return 0.0;
            return ((double) seatsTaken / capacity) * 100.0;
        }

        public double getAttendanceRate() {
            if (seatsTaken <= 0) return 0.0;
            return ((double) checkedIn / seatsTaken) * 100.0;
        }
    }
}
