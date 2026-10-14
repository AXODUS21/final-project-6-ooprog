package plano;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.*;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Self-contained automated test suite for verifying PlanO core functionalities,
 * design patterns, conflict detection, waitlist auto-promotion, and persistence.
 */
public class PlanOTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("Running PlanO Automated Test Suite");
        System.out.println("==================================================");

        // Use a test-specific directory so we don't mess up production data
        String testDataDir = "test_data";
        cleanDirectory(new File(testDataDir));

        DataStore dataStore = new DataStore(testDataDir);
        AuthService authService = new AuthService(dataStore);
        UserService userService = new UserService(dataStore);
        EventService eventService = new EventService(dataStore);
        RegistrationService registrationService = new RegistrationService(dataStore);
        FeedbackService feedbackService = new FeedbackService(dataStore);
        ReportService reportService = new ReportService(dataStore);

        testAuthentication(authService, dataStore);
        testSchedulingConflictDetection(eventService, dataStore);
        testCapacityAndWaitlistAutoPromotion(eventService, registrationService, dataStore);
        testStrategyPatternSorting(eventService, registrationService, dataStore);
        testStrategyFiltering(eventService, dataStore);
        testAttendanceCheckIn(registrationService, dataStore);
        testFeedbackAndRatings(feedbackService, dataStore);
        testReportingAndDashboards(reportService);
        testDataPersistenceReload(testDataDir);

        // Cleanup
        cleanDirectory(new File(testDataDir));

        System.out.println("==================================================");
        System.out.printf("Test Results: %d Passed, %d Failed\n", testsPassed, testsFailed);
        System.out.println("==================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            System.out.println(" [PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println(" [FAIL] " + testName);
            testsFailed++;
        }
    }

    private static void testAuthentication(AuthService authService, DataStore dataStore) {
        System.out.println("\n--- Testing Authentication & Account Creation ---");

        // Admin login
        AuthService.LoginResult adminLogin = authService.login("admin", "admin123");
        assertTrue("Admin can log in with valid credentials", adminLogin.success && adminLogin.user.isAdmin());

        // Invalid login
        AuthService.LoginResult badLogin = authService.login("admin", "wrongpassword");
        assertTrue("Invalid password fails login", !badLogin.success);

        // Register new attendee
        AuthService.RegisterResult regResult = authService.register("newuser", "pass123", "New User", "new@test.com", Role.ATTENDEE);
        assertTrue("New attendee registration succeeds", regResult.success);

        // Duplicate username rejected
        AuthService.RegisterResult dupUser = authService.register("newuser", "pass123", "Duplicate User", "dup@test.com", Role.ATTENDEE);
        assertTrue("Duplicate username registration is rejected", !dupUser.success);

        // Duplicate email rejected
        AuthService.RegisterResult dupEmail = authService.register("anotheruser", "pass123", "Another User", "new@test.com", Role.ATTENDEE);
        assertTrue("Duplicate email registration is rejected", !dupEmail.success);
    }

    private static void testSchedulingConflictDetection(EventService eventService, DataStore dataStore) {
        System.out.println("\n--- Testing Scheduling Conflict Detection ---");
        User organizer = dataStore.getUserRepository().findByUsername("courtney_org").get();

        LocalDateTime base = LocalDateTime.now().plusDays(30);
        LocalDateTime start1 = base.withHour(10).withMinute(0);
        LocalDateTime end1 = base.withHour(12).withMinute(0);

        // Event 1 at Main Auditorium
        EventService.EventResult res1 = eventService.createEvent(
                organizer, "Tech Expo", "Annual exhibition", "Conference",
                "Grand Hall B", start1, end1, 100
        );
        assertTrue("Primary event creation succeeds", res1.success);

        // Overlapping event at the SAME venue
        LocalDateTime startOverlap = base.withHour(11).withMinute(0);
        LocalDateTime endOverlap = base.withHour(13).withMinute(0);
        EventService.EventResult conflictRes = eventService.createEvent(
                organizer, "Conflicting Keynote", "Overlapping event", "Seminar",
                "Grand Hall B", startOverlap, endOverlap, 50
        );
        assertTrue("Conflicting event at the same venue is rejected", !conflictRes.success && conflictRes.message.contains("Scheduling Conflict Detected"));

        // Non-overlapping event at the same venue (later in the day)
        LocalDateTime startLater = base.withHour(13).withMinute(0);
        LocalDateTime endLater = base.withHour(15).withMinute(0);
        EventService.EventResult nonConflictRes = eventService.createEvent(
                organizer, "Afternoon Workshop", "No overlap", "Workshop",
                "Grand Hall B", startLater, endLater, 50
        );
        assertTrue("Non-overlapping event at the same venue succeeds", nonConflictRes.success);

        // Overlapping event at a DIFFERENT venue
        EventService.EventResult diffVenueRes = eventService.createEvent(
                organizer, "Concurrent Lab", "Different venue", "Workshop",
                "Computer Lab 101", startOverlap, endOverlap, 25
        );
        assertTrue("Overlapping event at a different venue succeeds", diffVenueRes.success);
    }

    private static void testCapacityAndWaitlistAutoPromotion(EventService eventService,
                                                            RegistrationService regService,
                                                            DataStore dataStore) {
        System.out.println("\n--- Testing Capacity, FIFO Waitlist & Auto-Promotion ---");
        User organizer = dataStore.getUserRepository().findByUsername("courtney_org").get();
        User userA = dataStore.getUserRepository().findByUsername("axellerosh").get();
        User userB = dataStore.getUserRepository().findByUsername("john_doe").get();
        User userC = dataStore.getUserRepository().findByUsername("jane_smith").get();

        // Create an event with capacity = 2
        LocalDateTime start = LocalDateTime.now().plusDays(40).withHour(9).withMinute(0);
        LocalDateTime end = LocalDateTime.now().plusDays(40).withHour(11).withMinute(0);
        EventService.EventResult eventRes = eventService.createEvent(
                organizer, "Micro Workshop Exclusive", "Cap 2", "Workshop",
                "Meeting Room 1", start, end, 2
        );
        String eventId = eventRes.event.getEventId();

        // Register userA (Spot 1 of 2) -> CONFIRMED
        RegistrationService.RegistrationResult r1 = regService.register(userA, eventId);
        assertTrue("1st registrant is CONFIRMED", r1.success && r1.registration.isConfirmed());

        // Register userB (Spot 2 of 2) -> CONFIRMED
        RegistrationService.RegistrationResult r2 = regService.register(userB, eventId);
        assertTrue("2nd registrant is CONFIRMED (Capacity reached)", r2.success && r2.registration.isConfirmed());

        // Register userC (Spot 3) -> Should go to WAITLIST position #1
        RegistrationService.RegistrationResult r3 = regService.register(userC, eventId);
        assertTrue("3rd registrant is placed on WAITLIST", r3.success && r3.registration.isWaitlisted());
        assertTrue("Waitlist position for userC is 1", regService.getWaitlistPosition(eventId, userC.getUserId()) == 1);

        // Duplicate registration check
        RegistrationService.RegistrationResult dupReg = regService.register(userA, eventId);
        assertTrue("Duplicate registration attempt is prevented", !dupReg.success);

        // Cancel userA's confirmed registration -> Triggers Auto-Promotion for userC!
        RegistrationService.CancellationResult cancelRes = regService.cancelRegistration(userA, r1.registration.getRegistrationId());
        assertTrue("Cancellation of userA succeeds", cancelRes.success);
        assertTrue("Waitlist Auto-Promotion promoted userC to CONFIRMED",
                cancelRes.promotedRegistration != null &&
                        cancelRes.promotedRegistration.getUserId().equals(userC.getUserId()) &&
                        cancelRes.promotedRegistration.isConfirmed()
        );

        // Confirm userC is now in confirmed list
        List<Registration> confirmedList = dataStore.getRegistrationRepository().findConfirmedByEvent(eventId);
        boolean userCConfirmed = confirmedList.stream().anyMatch(r -> r.getUserId().equals(userC.getUserId()));
        assertTrue("Repository state reflects userC as CONFIRMED", userCConfirmed);
    }

    private static void testStrategyPatternSorting(EventService eventService,
                                                   RegistrationService regService,
                                                   DataStore dataStore) {
        System.out.println("\n--- Testing Strategy Pattern (Sorting) ---");
        List<Event> allEvents = eventService.getAllEvents();

        // 1. Sort by Date Ascending
        EventSortStrategy dateAsc = new SortByDateAscStrategy();
        List<Event> sortedDateAsc = eventService.sortEvents(allEvents, dateAsc);
        boolean isAsc = true;
        for (int i = 0; i < sortedDateAsc.size() - 1; i++) {
            if (sortedDateAsc.get(i).getStartDateTime().isAfter(sortedDateAsc.get(i + 1).getStartDateTime())) {
                isAsc = false;
                break;
            }
        }
        assertTrue("SortByDateAscStrategy orders events from earliest to latest", isAsc);

        // 2. Sort by Date Descending
        EventSortStrategy dateDesc = new SortByDateDescStrategy();
        List<Event> sortedDateDesc = eventService.sortEvents(allEvents, dateDesc);
        boolean isDesc = true;
        for (int i = 0; i < sortedDateDesc.size() - 1; i++) {
            if (sortedDateDesc.get(i).getStartDateTime().isBefore(sortedDateDesc.get(i + 1).getStartDateTime())) {
                isDesc = false;
                break;
            }
        }
        assertTrue("SortByDateDescStrategy orders events from latest to earliest", isDesc);

        // 3. Sort by Title Alphabetical
        EventSortStrategy titleStrategy = new SortByTitleStrategy();
        List<Event> sortedTitle = eventService.sortEvents(allEvents, titleStrategy);
        boolean isAlphabetical = true;
        for (int i = 0; i < sortedTitle.size() - 1; i++) {
            if (sortedTitle.get(i).getTitle().compareToIgnoreCase(sortedTitle.get(i + 1).getTitle()) > 0) {
                isAlphabetical = false;
                break;
            }
        }
        assertTrue("SortByTitleStrategy orders events alphabetically A-Z", isAlphabetical);

        // 4. Sort by Popularity
        EventSortStrategy popStrategy = new SortByPopularityStrategy();
        List<Event> sortedPop = eventService.sortEvents(allEvents, popStrategy);
        int prevCount = Integer.MAX_VALUE;
        boolean isPop = true;
        for (Event e : sortedPop) {
            int count = regService.getConfirmedCount(e.getEventId());
            if (count > prevCount) {
                isPop = false;
                break;
            }
            prevCount = count;
        }
        assertTrue("SortByPopularityStrategy orders events by registration count descending", isPop);
    }

    private static void testStrategyFiltering(EventService eventService, DataStore dataStore) {
        System.out.println("\n--- Testing Event Filtering Criteria ---");
        List<Event> allEvents = eventService.getAllEvents();

        EventFilterCriteria criteria = new EventFilterCriteria();
        criteria.setCategory("Conference");
        List<Event> filtered = eventService.filterEvents(allEvents, criteria);
        boolean allMatchCategory = filtered.stream().allMatch(e -> "Conference".equalsIgnoreCase(e.getCategory()));
        assertTrue("Filter by category 'Conference' returns only conference events", allMatchCategory && !filtered.isEmpty());

        // Text query
        criteria.reset();
        criteria.setTextQuery("Java");
        List<Event> textFiltered = eventService.filterEvents(allEvents, criteria);
        boolean allMatchText = textFiltered.stream().allMatch(e ->
                (e.getTitle() != null && e.getTitle().contains("Java")) ||
                        (e.getDescription() != null && e.getDescription().contains("Java")));
        assertTrue("Filter by text query 'Java' matches title or description", allMatchText && !textFiltered.isEmpty());
    }

    private static void testAttendanceCheckIn(RegistrationService regService, DataStore dataStore) {
        System.out.println("\n--- Testing Attendance Check-In ---");
        User organizer = dataStore.getUserRepository().findByUsername("courtney_org").get();
        Event completedEvent = dataStore.getEventRepository().findById("EVT-1006").get();
        User attendee = dataStore.getUserRepository().findByUsername("jane_smith").get();

        // Check in attendee
        RegistrationService.CheckInResult checkInRes = regService.checkInAttendee(organizer, completedEvent.getEventId(), attendee.getUserId());
        assertTrue("Organizer can check in a confirmed attendee", checkInRes.success);
        assertTrue("Checked in attendee status is CHECKED_IN", checkInRes.registration.isCheckedIn());

        // Duplicate check-in rejected
        RegistrationService.CheckInResult dupCheckIn = regService.checkInAttendee(organizer, completedEvent.getEventId(), attendee.getUserId());
        assertTrue("Duplicate check-in attempt is flagged and prevented", !dupCheckIn.success);
    }

    private static void testFeedbackAndRatings(FeedbackService feedbackService, DataStore dataStore) {
        System.out.println("\n--- Testing Post-Event Feedback & Ratings ---");
        Event completedEvent = dataStore.getEventRepository().findById("EVT-1006").get();
        User attendee = dataStore.getUserRepository().findByUsername("axellerosh").get();

        // Submit feedback for completed event
        FeedbackService.FeedbackResult fbRes = feedbackService.submitFeedback(
                attendee, completedEvent.getEventId(), 5, "Remarkable event, thoroughly enjoyed every part!"
        );
        assertTrue("Attendee can submit feedback for a completed event", fbRes.success);

        // Average rating calculation
        double avg = feedbackService.getAverageRating(completedEvent.getEventId());
        assertTrue("Average rating is properly computed and within 1-5 range", avg >= 1.0 && avg <= 5.0);

        // Attempt feedback on upcoming event (should fail)
        Event upcomingEvent = dataStore.getEventRepository().findById("EVT-1001").get();
        FeedbackService.FeedbackResult badFb = feedbackService.submitFeedback(
                attendee, upcomingEvent.getEventId(), 5, "Premature review"
        );
        assertTrue("Feedback cannot be submitted for upcoming uncompleted events", !badFb.success);
    }

    private static void testReportingAndDashboards(ReportService reportService) {
        System.out.println("\n--- Testing Analytics & Reporting ---");
        ReportService.AdminDashboardData dash = reportService.getAdminDashboard();

        assertTrue("Admin dashboard computes total events > 0", dash.totalEvents > 0);
        assertTrue("Admin dashboard computes total registered users > 0", dash.totalUsers > 0);
        assertTrue("Admin dashboard calculates category popularity map", dash.categoryPopularity != null && !dash.categoryPopularity.isEmpty());
        assertTrue("Admin dashboard generates organizer activity stats", dash.organizerStats != null && !dash.organizerStats.isEmpty());
    }

    private static void testDataPersistenceReload(String testDataDir) {
        System.out.println("\n--- Testing File-Based JSON Persistence Reload ---");
        // Create fresh DataStore pointing to the same test directory
        DataStore reloadedStore = new DataStore(testDataDir);

        int userCount = reloadedStore.getUserRepository().size();
        int eventCount = reloadedStore.getEventRepository().size();
        int regCount = reloadedStore.getRegistrationRepository().size();

        assertTrue("Reloaded DataStore preserves user records", userCount > 0);
        assertTrue("Reloaded DataStore preserves event records", eventCount > 0);
        assertTrue("Reloaded DataStore preserves registration records", regCount > 0);
    }

    private static void cleanDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }
}
