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

        // Attendance rate (checked-in vs. registered) over completed events only
        for (Event e : allEvents) {
            if (e.isCompleted()) {
                data.completedEventRegistrations += regRepo.countConfirmedByEvent(e.getEventId());
                data.completedEventCheckIns += regRepo.countCheckedInByEvent(e.getEventId());
            }
        }
        data.overallAttendanceRate = data.completedEventRegistrations > 0
                ? ((double) data.completedEventCheckIns / data.completedEventRegistrations) * 100.0
                : 0.0;

        // Category Popularity: seats taken per category, cancelled events excluded
        Map<String, Integer> catCounts = new LinkedHashMap<>();
        for (Event e : allEvents) {
            if (e.isCancelled()) {
                continue;
            }
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            int count = regRepo.countConfirmedByEvent(e.getEventId());
            catCounts.put(cat, catCounts.getOrDefault(cat, 0) + count);
        }
        data.categoryPopularity = catCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

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
                        totalRegistrations += regRepo.countConfirmedByEvent(ev.getEventId());
                        totalCheckedIn += regRepo.countCheckedInByEvent(ev.getEventId());
                    }
                    orgList.add(new OrganizerStats(u.getUserId(), u.getFullName(), u.getUsername(), eventCount, totalRegistrations, totalCheckedIn));
                }
            }
        }
        orgList.sort(Comparator.comparingInt(OrganizerStats::getEventCount).reversed());
        data.organizerStats = orgList;

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
        int totalReg = 0;
        int totalWait = 0;
        int totalCheckIn = 0;

        for (Event ev : orgEvents) {
            int confirmed = regRepo.countConfirmedByEvent(ev.getEventId());
            int waitlisted = regRepo.countWaitlistedByEvent(ev.getEventId());
            int checkedIn = regRepo.countCheckedInByEvent(ev.getEventId());
            double avgRating = dataStore.getFeedbackRepository().getAverageRatingForEvent(ev.getEventId());
            int reviewCount = dataStore.getFeedbackRepository().findByEvent(ev.getEventId()).size();

            if (!ev.isCancelled()) {
                totalReg += confirmed;
                totalWait += waitlisted;
                totalCheckIn += checkedIn;
            }

            performanceList.add(new EventPerformance(
                    ev.getEventId(),
                    ev.getTitle(),
                    ev.getStatus(),
                    ev.getCapacity(),
                    confirmed,
                    waitlisted,
                    checkedIn,
                    avgRating,
                    reviewCount
            ));
        }

        data.totalRegistrations = totalReg;
        data.totalWaitlisted = totalWait;
        data.totalCheckedIn = totalCheckIn;
        data.eventPerformances = performanceList;

        return data;
    }

    // ==================== DATA CARRIER CLASSES ====================

    public static class AdminDashboardData {
        public int totalEvents;
        public int upcomingEvents;
        public int ongoingEvents;
        /** Events whose end time has passed but that were never marked Completed. */
        public int awaitingCompletionEvents;
        public int completedEvents;
        public int cancelledEvents;

        public int totalUsers;
        public int totalOrganizers;
        public int totalAttendees;

        /** Active registrations (confirmed + checked in + waitlisted) of events that are not cancelled. */
        public int totalRegistrations;
        public int confirmedRegistrations;
        public int waitlistedRegistrations;
        public int checkedInAttendees;

        /** Seats taken at completed events, and how many of those people checked in. */
        public int completedEventRegistrations;
        public int completedEventCheckIns;
        public double overallAttendanceRate;

        public Map<String, Integer> categoryPopularity;
        public List<OrganizerStats> organizerStats;
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
        public int totalEvents;
        public int upcomingEvents;
        public int ongoingEvents;
        /** Events whose end time has passed but that were never marked Completed. */
        public int awaitingCompletionEvents;
        public int completedEvents;
        public int cancelledEvents;

        public int totalRegistrations;
        public int totalWaitlisted;
        public int totalCheckedIn;

        public List<EventPerformance> eventPerformances;
    }

    public static class EventPerformance {
        public final String eventId;
        public final String title;
        public final EventStatus status;
        public final int capacity;
        public final int confirmed;
        public final int waitlisted;
        public final int checkedIn;
        public final double averageRating;
        public final int reviewCount;

        public EventPerformance(String eventId, String title, EventStatus status, int capacity,
                                int confirmed, int waitlisted, int checkedIn, double averageRating, int reviewCount) {
            this.eventId = eventId;
            this.title = title;
            this.status = status;
            this.capacity = capacity;
            this.confirmed = confirmed;
            this.waitlisted = waitlisted;
            this.checkedIn = checkedIn;
            this.averageRating = averageRating;
            this.reviewCount = reviewCount;
        }

        public double getFillRate() {
            if (capacity <= 0) return 0.0;
            return ((double) confirmed / capacity) * 100.0;
        }

        public double getAttendanceRate() {
            int eligible = confirmed;
            if (eligible <= 0) return 0.0;
            return ((double) checkedIn / eligible) * 100.0;
        }
    }
}
