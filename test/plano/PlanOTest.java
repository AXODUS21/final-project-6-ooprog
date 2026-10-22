package plano;

import plano.model.*;
import plano.repository.DataLoadException;
import plano.repository.DataSaveException;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.*;
import plano.util.ConsoleIO;
import plano.util.InputValidator;
import plano.util.SimpleJson;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        RegistrationService registrationService = new RegistrationService(dataStore);
        UserService userService = new UserService(dataStore, registrationService);
        EventService eventService = new EventService(dataStore, registrationService);
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
        testEnhancedInputValidation(authService, eventService, registrationService, feedbackService, dataStore);
        testEmptyDatabaseAndNumericFormatting();
        testRuleFixes();
        testEncapsulation();
        testAuditFixes();

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
        AuthService.RegisterResult regResult = authService.register("newuser", "pass123", "New User", "new@test.com");
        assertTrue("New attendee registration succeeds", regResult.success);

        // Duplicate username rejected
        AuthService.RegisterResult dupUser = authService.register("newuser", "pass123", "Duplicate User", "dup@test.com");
        assertTrue("Duplicate username registration is rejected", !dupUser.success);

        // Duplicate email rejected
        AuthService.RegisterResult dupEmail = authService.register("anotheruser", "pass123", "Another User", "new@test.com");
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

        // Confirm the repository now holds userC's registration as CONFIRMED
        boolean userCConfirmed = dataStore.getRegistrationRepository().findByEventAndUser(eventId, userC.getUserId())
                .map(Registration::isConfirmed).orElse(false);
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
            int count = regService.getSeatsTaken(e.getEventId());
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

        assertTrue("Admin dashboard computes total events > 0", dash.getTotalEvents() > 0);
        assertTrue("Admin dashboard computes total registered users > 0", dash.getTotalUsers() > 0);
        assertTrue("Admin dashboard calculates category popularity map", !dash.getCategoryPopularity().isEmpty());
        assertTrue("Admin dashboard generates organizer activity stats", !dash.getOrganizerStats().isEmpty());
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

    private static void testEnhancedInputValidation(AuthService authService, EventService eventService,
                                                    RegistrationService registrationService, FeedbackService feedbackService,
                                                    DataStore dataStore) {
        System.out.println("\n--- Testing Enhanced Input Validation & Business Invariants ---");

        // 1. Username pattern validation
        AuthService.RegisterResult shortUser = authService.register("ab", "pass123", "Short User", "short@test.com");
        assertTrue("Username under 3 chars is rejected", !shortUser.success);

        AuthService.RegisterResult spaceUser = authService.register("bad user", "pass123", "Space User", "space@test.com");
        assertTrue("Username with spaces is rejected", !spaceUser.success);

        // 2. Password validation
        AuthService.RegisterResult blankPass = authService.register("validuser1", "    ", "Blank Pass User", "blank@test.com");
        assertTrue("Password with whitespace only is rejected", !blankPass.success);

        AuthService.RegisterResult shortPass = authService.register("validuser1b", "ab1", "Short Pass User", "shortp@test.com");
        assertTrue("Password under 6 chars is rejected", !shortPass.success);

        AuthService.RegisterResult noNumPass = authService.register("validuser1c", "password", "No Digit Pass User", "nonum@test.com");
        assertTrue("Password without digits is rejected", !noNumPass.success);

        // 3. Full name validation
        AuthService.RegisterResult numName = authService.register("validuser2", "pass123", "John123", "num@test.com");
        assertTrue("Full name containing numbers is rejected", !numName.success);

        // 4. Non-existent category rejection
        User organizer = dataStore.getUserRepository().findByUsername("courtney_org").get();
        LocalDateTime futureStart = LocalDateTime.now().plusDays(60).withHour(10).withMinute(0);
        LocalDateTime futureEnd = LocalDateTime.now().plusDays(60).withHour(12).withMinute(0);
        EventService.EventResult badCatEvent = eventService.createEvent(
                organizer, "Invalid Category Event", "Desc", "NonExistentCategory",
                "Room A", futureStart, futureEnd, 20
        );
        assertTrue("Creating an event with non-existent category is rejected", !badCatEvent.success);

        // 5. Short event title rejection
        EventService.EventResult shortTitle = eventService.createEvent(
                organizer, "Hi", "Desc", "Conference",
                "Room A", futureStart, futureEnd, 20
        );
        assertTrue("Creating an event with title shorter than 3 chars is rejected", !shortTitle.success);

        // 6. Capacity bounds
        EventService.EventResult zeroCap = eventService.createEvent(
                organizer, "Zero Cap Event", "Desc", "Conference",
                "Room A", futureStart, futureEnd, 0
        );
        assertTrue("Creating an event with capacity <= 0 is rejected", !zeroCap.success);

        // Event scheduled beyond 2-year horizon is rejected
        LocalDateTime distantFutureStart = LocalDateTime.now().plusYears(3);
        LocalDateTime distantFutureEnd = distantFutureStart.plusHours(2);
        EventService.EventResult horizonEvt = eventService.createEvent(
                organizer, "Distant Future Event", "Too far", "Conference",
                "Hall Z", distantFutureStart, distantFutureEnd, 50
        );
        assertTrue("Event scheduled beyond 2-year horizon is rejected", !horizonEvt.success);

        // 7. Rescheduling an upcoming event into the past is rejected
        Event evt1 = dataStore.getEventRepository().findById("EVT-1001").get();
        EventService.EventResult pastResched = eventService.updateEvent(
                organizer, evt1.getEventId(), null, null, null, null,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), -1
        );
        assertTrue("Rescheduling an upcoming event into the past is rejected", !pastResched.success);

        // 8. Capacity reduction below confirmed attendees is rejected
        // EVT-1001 has 3 confirmed attendees in default data
        EventService.EventResult badCapReduce = eventService.updateEvent(
                organizer, "EVT-1001", null, null, null, null, null, null, 1
        );
        assertTrue("Reducing capacity below current confirmed attendee count is rejected", !badCapReduce.success);

        // 9. Organizer self-registration is rejected
        RegistrationService.RegistrationResult selfReg = registrationService.register(organizer, "EVT-1001");
        assertTrue("Organizer cannot register as an attendee for their own event", !selfReg.success);

        // 10. Organizer reviewing own event is rejected
        FeedbackService.FeedbackResult selfReview = feedbackService.submitFeedback(organizer, "EVT-1006", 5, "I was great!");
        assertTrue("Organizer cannot submit feedback for their own event", !selfReview.success);

        // 11. Attendee schedule conflict detection
        User attendee = dataStore.getUserRepository().findByUsername("axellerosh").get();
        // axellerosh is confirmed for EVT-1001
        Event evt1Ref = dataStore.getEventRepository().findById("EVT-1001").get();
        // Create an overlapping event at a different venue
        EventService.EventResult overlapEvt = eventService.createEvent(
                organizer, "Overlapping Parallel Event", "Parallel", "Seminar",
                "Hall Z", evt1Ref.getStartDateTime(), evt1Ref.getEndDateTime(), 50
        );
        assertTrue("Parallel event creation succeeded", overlapEvt.success);
        RegistrationService.RegistrationResult conflictReg = registrationService.register(attendee, overlapEvt.event.getEventId());
        assertTrue("Attendee registering for overlapping event is flagged with Schedule Conflict",
                !conflictReg.success && conflictReg.message.contains("Schedule Conflict"));

        // 12. Minimum duration enforcement (less than 15 mins rejected)
        LocalDateTime shortStart = LocalDateTime.now().plusDays(70).withHour(10).withMinute(0);
        LocalDateTime shortEnd = shortStart.plusMinutes(5); // 5 mins only
        EventService.EventResult tooShortEvent = eventService.createEvent(
                organizer, "Too Short Event", "Short", "Seminar",
                "Room Q", shortStart, shortEnd, 20
        );
        assertTrue("Event duration under 15 minutes is rejected", !tooShortEvent.success);

        // 13. Duplicate event detection for same organizer
        EventService.EventResult dupOrgEvent = eventService.createEvent(
                organizer, "Overlapping Parallel Event", "Parallel again", "Seminar",
                "Hall Z", evt1Ref.getStartDateTime(), evt1Ref.getEndDateTime(), 50
        );
        assertTrue("Duplicate event for same organizer at same time & venue is rejected", !dupOrgEvent.success);

        // 14. Safeguard against deactivating organizer with upcoming events
        UserService userService = users(dataStore);
        User admin = dataStore.getUserRepository().findByUsername("admin").get();
        OperationResult deactRes = userService.deactivateUser(admin, organizer.getUserId());
        assertTrue("Deactivating an organizer with active upcoming events is prevented",
                !deactRes.success && deactRes.message.contains("active upcoming event"));

        // 15. Spam review comment detection
        FeedbackService.FeedbackResult spamReview = feedbackService.submitFeedback(attendee, "EVT-1006", 5, "aaaaaaa");
        assertTrue("Repetitive spam review comment is rejected", !spamReview.success);

        // 16. Date range filter validation
        EventFilterCriteria criteria = new EventFilterCriteria();
        boolean badDateRange = criteria.setDateRange(LocalDateTime.now().plusDays(5), LocalDateTime.now());
        assertTrue("Inverted date range filter (from > to) is rejected", !badDateRange);

        // 17. Single-line sanitization
        String sanitized = InputValidator.sanitizeSingleLine("Line1\nLine2\tLine3");
        assertTrue("Single-line sanitization converts newlines and tabs to spaces", "Line1 Line2 Line3".equals(sanitized));
    }

    private static void testEmptyDatabaseAndNumericFormatting() {
        System.out.println("\n--- Testing Empty Database & Numeric/ID Format Validations ---");

        // 1. Empty database login rejection
        String emptyDir = "test_data/empty";
        cleanDirectory(new File(emptyDir));
        DataStore emptyStore = new DataStore(emptyDir);
        // Clear all users to simulate empty user database
        emptyStore.getUserRepository().clear();
        AuthService emptyAuth = new AuthService(emptyStore);

        AuthService.LoginResult loginOnEmpty = emptyAuth.login("admin", "admin123");
        assertTrue("Login is prevented when no registered accounts exist in the database",
                !loginOnEmpty.success && loginOnEmpty.message.contains("No registered accounts exist"));
        cleanDirectory(new File(emptyDir));

        // 2. Leading zero validation
        assertTrue("Number with leading zero '01' is detected", InputValidator.hasLeadingZeroes("01"));
        assertTrue("Number with leading zero '007' is detected", InputValidator.hasLeadingZeroes("007"));
        assertTrue("Single '0' is not considered leading zeroes", !InputValidator.hasLeadingZeroes("0"));
        assertTrue("Normal positive number '10' has no leading zeroes", !InputValidator.hasLeadingZeroes("10"));

        // 3. Decimal and trailing zero validation for integer inputs
        assertTrue("Number with decimal/trailing zero '1.0' is detected", InputValidator.hasDecimalOrTrailingZeroes("1.0"));
        assertTrue("Number with trailing zeroes '10.00' is detected", InputValidator.hasDecimalOrTrailingZeroes("10.00"));
        assertTrue("Standard integer '50' does not have decimal/trailing zeroes", !InputValidator.hasDecimalOrTrailingZeroes("50"));

        // 4. ID format validators
        assertTrue("'EVT-1001' is valid Event ID format", InputValidator.isValidEventId("EVT-1001"));
        assertTrue("'invalid-evt' is rejected as Event ID", !InputValidator.isValidEventId("invalid-evt"));
        assertTrue("'USR-1004' is valid User ID format", InputValidator.isValidUserId("USR-1004"));
        assertTrue("'REG-1001' is valid Registration ID format", InputValidator.isValidRegistrationId("REG-1001"));
        assertTrue("'CAT-101' is valid Category ID format", InputValidator.isValidCategoryId("CAT-101"));

        // 5. Horizon and cancel token validators
        assertTrue("Now plus 1 year is within horizon", InputValidator.isWithinMaxHorizon(LocalDateTime.now().plusYears(1)));
        assertTrue("Now plus 3 years exceeds horizon", !InputValidator.isWithinMaxHorizon(LocalDateTime.now().plusYears(3)));
        assertTrue("'cancel' is recognized as cancel token", ConsoleIO.isCancelToken("cancel"));
        assertTrue("'0' is recognized as cancel token", ConsoleIO.isCancelToken("0"));
        assertTrue("':back' is recognized as cancel token", ConsoleIO.isCancelToken(":back"));
        assertTrue("'normal input' is not cancel token", !ConsoleIO.isCancelToken("normal input"));
    }

    /**
     * Regression checks for the rule fixes. Each group uses its own freshly seeded
     * data folder so the checks do not depend on each other or on the tests above.
     */
    private static void testRuleFixes() {
        System.out.println("\n--- Testing Rule Fixes (Regression Checks) ---");

        // Rejected edits change nothing
        DataStore edits = freshStore("edits");
        EventService editEvents = events(edits);
        Event summit = edits.getEventRepository().findById("EVT-1001").get();
        String oldTitle = summit.getTitle();
        LocalDateTime oldStart = summit.getStartDateTime();
        EventService.EventResult badEdit = editEvents.updateEvent(userNamed(edits, "courtney_org"), "EVT-1001",
                "Renamed Summit", "New description", null, null,
                oldStart.plusDays(1), summit.getEndDateTime().plusDays(1), 1);
        assertTrue("Rejected event edit leaves the title and times unchanged",
                !badEdit.success && summit.getTitle().equals(oldTitle) && summit.getStartDateTime().equals(oldStart));

        User john = userNamed(edits, "john_doe");
        OperationResult badProfile = users(edits).updateProfile(john.getUserId(), "Johnny Changed", "admin@plano.edu", null);
        assertTrue("Rejected profile update leaves the name unchanged",
                !badProfile.success && john.getFullName().equals("John Doe"));

        // Raising capacity promotes the waitlist (emily_watson is #1 on the full EVT-1002)
        DataStore capacity = freshStore("capacity");
        EventService.EventResult grow = events(capacity).updateEvent(userNamed(capacity, "courtney_org"), "EVT-1002",
                null, null, null, null, null, null, 4);
        Registration emilyWaiting = capacity.getRegistrationRepository()
                .findByEventAndUser("EVT-1002", userNamed(capacity, "emily_watson").getUserId()).get();
        assertTrue("Raising capacity promotes the first waitlisted attendee", grow.success && emilyWaiting.isConfirmed());

        // Promotion skips someone it would double-book and promotes the next person instead
        DataStore booking = freshStore("double-booking");
        EventService bookingEvents = events(booking);
        RegistrationService bookingRegs = new RegistrationService(booking);
        User org = userNamed(booking, "courtney_org");
        LocalDateTime slot = LocalDateTime.now().plusDays(50).withHour(10).withMinute(0);
        Event sessionA = bookingEvents.createEvent(org, "Parallel Session A", "A", "Seminar", "Room A", slot, slot.plusHours(2), 1).event;
        Event sessionB = bookingEvents.createEvent(org, "Parallel Session B", "B", "Seminar", "Room B", slot, slot.plusHours(2), 1).event;
        Registration axelSeat = bookingRegs.register(userNamed(booking, "axellerosh"), sessionA.getEventId()).registration;
        Registration johnSeat = bookingRegs.register(userNamed(booking, "john_doe"), sessionB.getEventId()).registration;
        bookingRegs.register(userNamed(booking, "jane_smith"), sessionA.getEventId()); // waitlisted on A
        bookingRegs.register(userNamed(booking, "jane_smith"), sessionB.getEventId()); // waitlisted on B
        bookingRegs.register(userNamed(booking, "mark_lee"), sessionB.getEventId());   // waitlisted on B behind jane
        bookingRegs.cancelRegistration(userNamed(booking, "axellerosh"), axelSeat.getRegistrationId()); // jane gets A
        RegistrationService.CancellationResult freedB = bookingRegs.cancelRegistration(userNamed(booking, "john_doe"), johnSeat.getRegistrationId());
        String janeId = userNamed(booking, "jane_smith").getUserId();
        assertTrue("Promotion skips an attendee it would double-book and promotes the next one",
                booking.getRegistrationRepository().findByEventAndUser(sessionA.getEventId(), janeId).get().isConfirmed()
                        && booking.getRegistrationRepository().findByEventAndUser(sessionB.getEventId(), janeId).get().isWaitlisted()
                        && freedB.promotedUser != null && "mark_lee".equals(freedB.promotedUser.getUsername()));

        // Cancelling events, and the forward-only status lifecycle
        DataStore lifecycle = freshStore("lifecycle");
        EventService lifecycleEvents = events(lifecycle);
        RegistrationService lifecycleRegs = new RegistrationService(lifecycle);
        User courtney = userNamed(lifecycle, "courtney_org");
        EventService.EventResult cancelled = lifecycleEvents.cancelEvent(courtney, "EVT-1002");
        long stillActive = lifecycleRegs.getRegistrationsForEvent("EVT-1002").stream()
                .filter(r -> r.isConfirmed() || r.isWaitlisted()).count();
        assertTrue("Cancelling an event cancels its confirmed and waitlisted registrations", cancelled.success && stillActive == 0);
        RegistrationService.CancellationResult afterCancel = lifecycleRegs.cancelRegistration(userNamed(lifecycle, "axellerosh"), "REG-1004");
        assertTrue("No waitlist promotion happens on a cancelled event", !afterCancel.success && afterCancel.promotedUser == null);
        ReportService.AdminDashboardData lifecycleDash = new ReportService(lifecycle).getAdminDashboard();
        assertTrue("Cancelled events are left out of category popularity",
                lifecycleDash.getCategoryPopularity().getOrDefault("Workshop", 0) == 0);
        assertTrue("A completed event cannot be cancelled", !lifecycleEvents.cancelEvent(courtney, "EVT-1006").success);
        assertTrue("An event cannot be marked Ongoing before its start date",
                !lifecycleEvents.updateEventStatus(courtney, "EVT-1001", EventStatus.ONGOING).success);
        // A session that started an hour ago can legitimately be moved to Ongoing
        User gavin = userNamed(lifecycle, "gavin_org");
        Event liveSession = new Event("EVT-9001", "Live Session", "Already running", gavin.getUserId(), "Seminar",
                "Room L", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), 10);
        lifecycle.getEventRepository().add(liveSession);
        assertTrue("A started event can be marked Ongoing",
                lifecycleEvents.updateEventStatus(gavin, "EVT-9001", EventStatus.ONGOING).success);
        assertTrue("An Ongoing event cannot move back to Upcoming",
                !lifecycleEvents.updateEventStatus(gavin, "EVT-9001", EventStatus.UPCOMING).success);

        // Events whose end time has passed no longer count as upcoming
        DataStore ended = freshStore("ended-events");
        for (String id : new String[]{"EVT-1003", "EVT-1004"}) {
            Event e = ended.getEventRepository().findById(id).get();
            e.updateDetails(e.getTitle(), e.getDescription(), e.getCategory(), e.getVenue(),
                    LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3).plusHours(2), e.getCapacity());
        }
        ReportService.AdminDashboardData endedDash = new ReportService(ended).getAdminDashboard();
        assertTrue("Ended events are counted as awaiting completion, not Upcoming",
                endedDash.getUpcomingEvents() == 3 && endedDash.getAwaitingCompletionEvents() == 2);
        assertTrue("An organizer whose events have all ended can be deactivated",
                users(ended).deactivateUser(userNamed(ended, "admin"), userNamed(ended, "gavin_org").getUserId()).success);

        // Check-in window and whole-minute event times
        DataStore timing = freshStore("timing");
        RegistrationService.CheckInResult early = new RegistrationService(timing)
                .checkInAttendee(userNamed(timing, "courtney_org"), "EVT-1001", "USR-1004");
        assertTrue("Check-in is refused before the event day", !early.success && early.message.contains("event day"));
        Event timedSummit = timing.getEventRepository().findById("EVT-1001").get();
        assertTrue("Event times are stored as whole minutes",
                timedSummit.getStartDateTime().getSecond() == 0 && timedSummit.getStartDateTime().getNano() == 0
                        && timedSummit.getEndDateTime().getNano() == 0);
        EventService.EventResult backToBack = events(timing).createEvent(userNamed(timing, "gavin_org"),
                "Evening Session", "Right after the summit", "Seminar", "Grand Auditorium A",
                timedSummit.getEndDateTime(), timedSummit.getEndDateTime().plusHours(1), 20);
        assertTrue("An event may start exactly when another ends at the same venue", backToBack.success);
        Path timingEvents = Paths.get("test_data", "timing", "events.json");
        writeText(timingEvents, readText(timingEvents).replaceFirst("(\"endDateTime\": \"[0-9T:-]+)\"", "$1.170016\""));
        Event reloadedSummit = new DataStore(Paths.get("test_data", "timing").toString())
                .getEventRepository().findById("EVT-1001").get();
        assertTrue("Saved times with fractions of a second are rounded to the minute on load",
                reloadedSummit.getEndDateTime().getNano() == 0);

        // Category IDs are never reused
        DataStore categories = freshStore("category-ids");
        String gamingId = categories.nextCategoryId();
        categories.getCategoryRepository().add(new Category(gamingId, "Gaming", "Video game events"));
        categories.getCategoryRepository().delete("CAT-106");
        String esportsId = categories.nextCategoryId();
        categories.getCategoryRepository().add(new Category(esportsId, "Esports", "Competitive gaming"));
        assertTrue("A new category never reuses the ID of an existing one",
                !gamingId.equals(esportsId) && categories.getCategoryRepository().existsByName("Gaming"));

        // A malformed data file stops loading instead of being overwritten later
        freshStore("corrupt");
        Path corruptEvents = Paths.get("test_data", "corrupt", "events.json");
        Path corruptRegs = Paths.get("test_data", "corrupt", "registrations.json");
        String regsBefore = readText(corruptRegs);
        writeText(corruptEvents, readText(corruptEvents)
                .replaceFirst("\"startDateTime\": \"[^\"]+\"", "\"startDateTime\": \"2026-13-45T99:00:00\""));
        boolean refused = false;
        try {
            new DataStore(Paths.get("test_data", "corrupt").toString());
        } catch (DataLoadException e) {
            refused = e.getMessage().contains("events.json");
        }
        assertTrue("A malformed data file stops loading and names the file", refused);
        assertTrue("A malformed data file leaves the other files untouched", regsBefore.equals(readText(corruptRegs)));

        // Reports
        ReportService.AdminDashboardData reportDash = new ReportService(freshStore("reports")).getAdminDashboard();
        assertTrue("Attendance rate only counts completed events (2 of 3 checked in)",
                reportDash.getCompletedEventCheckIns() == 2 && reportDash.getCompletedEventRegistrations() == 3
                        && Math.abs(reportDash.getOverallAttendanceRate() - 66.67) < 0.01);
        assertTrue("Registration breakdown adds up to the total",
                reportDash.getConfirmedRegistrations() + reportDash.getCheckedInAttendees()
                        + reportDash.getWaitlistedRegistrations() == reportDash.getTotalRegistrations());

        // Accounts: sign-up, deactivation, login messages
        DataStore accounts = freshStore("accounts");
        AuthService accountAuth = new AuthService(accounts);
        UserService accountUsers = users(accounts);
        User admin = userNamed(accounts, "admin");
        AuthService.RegisterResult signUp = accountAuth.register("new_member", "pass123", "New Member", "member@test.com");
        assertTrue("Sign-up always creates an Attendee account", signUp.success && signUp.user.isAttendee());
        String axelId = userNamed(accounts, "axellerosh").getUserId();
        OperationResult deactivated = accountUsers.deactivateUser(admin, axelId);
        assertTrue("Deactivating an attendee cancels their upcoming registrations",
                deactivated.success && !accounts.getRegistrationRepository().findByEventAndUser("EVT-1001", axelId).isPresent());
        Registration emilyAfter = accounts.getRegistrationRepository()
                .findByEventAndUser("EVT-1002", userNamed(accounts, "emily_watson").getUserId()).get();
        assertTrue("Seats freed by a deactivation go to the waitlist", emilyAfter.isConfirmed());
        AuthService.LoginResult wrongPassword = accountAuth.login("axellerosh", "wrong-password-1");
        assertTrue("A wrong password does not reveal that an account is deactivated",
                !wrongPassword.success && !wrongPassword.message.contains("deactivated"));
        assertTrue("Reactivating an already active account is reported",
                !accountUsers.reactivateUser(admin, userNamed(accounts, "john_doe").getUserId()).success);

        // Reviews require a check-in
        DataStore reviews = freshStore("reviews");
        FeedbackService reviewService = new FeedbackService(reviews);
        User jane = userNamed(reviews, "jane_smith");
        assertTrue("An attendee who was never checked in cannot review",
                !reviewService.submitFeedback(jane, "EVT-1006", 3, "I registered but did not attend").success);
        new RegistrationService(reviews).checkInAttendee(userNamed(reviews, "courtney_org"), "EVT-1006", jane.getUserId());
        assertTrue("The same attendee can review once checked in",
                reviewService.submitFeedback(jane, "EVT-1006", 3, "Good session overall").success);

        // Demo data matches the README demo
        List<Registration> demoWaitlist = reviews.getRegistrationRepository().findWaitlistedByEvent("EVT-1002");
        assertTrue("Demo data waitlists emily_watson on EVT-1002",
                !demoWaitlist.isEmpty() && demoWaitlist.get(0).getUserId().equals(userNamed(reviews, "emily_watson").getUserId()));
    }

    /**
     * Checks the rules the model classes now enforce themselves, and that saved data
     * loads back through their constructors unchanged.
     */
    private static void testEncapsulation() {
        System.out.println("\n--- Testing Encapsulation & Model Rules ---");

        boolean rejected = false;
        try {
            new Registration("REG-9001", "EVT-1001", "USR-1004", RegistrationStatus.CHECKED_IN);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        assertTrue("A new registration cannot start as Checked In", rejected);

        DataStore store = freshStore("encapsulation");
        User axel = userNamed(store, "axellerosh");
        assertTrue("Passwords are checked without being exposed",
                axel.passwordMatches("user123") && !axel.passwordMatches("wrong-password-1"));

        boolean adminPromoted = true;
        try {
            userNamed(store, "admin").promoteToOrganizer();
        } catch (IllegalStateException e) {
            adminPromoted = false;
        }
        assertTrue("Only attendees can be promoted to Organizer", !adminPromoted);

        // Change some state, save, and load it back through the private constructors
        users(store).deactivateUser(userNamed(store, "admin"), userNamed(store, "mark_lee").getUserId());
        Feedback review = store.getFeedbackRepository().findById("FB-1002").get();
        review.revise(2, "Revised after the hackathon", review.getSubmittedAt());
        store.saveAll();

        DataStore reloaded = new DataStore(Paths.get("test_data", "encapsulation").toString());
        Event orientation = reloaded.getEventRepository().findById("EVT-1006").get();
        Registration checkedIn = reloaded.getRegistrationRepository().findById("REG-1008").get();
        Feedback reloadedReview = reloaded.getFeedbackRepository().findById("FB-1002").get();
        assertTrue("Saved events load back with their status and organizer",
                orientation.isCompleted() && "USR-1002".equals(orientation.getOrganizerId()));
        assertTrue("Saved registrations load back with their check-in time",
                checkedIn.isCheckedIn() && checkedIn.getCheckedInAt() != null);
        assertTrue("Saved reviews and accounts load back with their latest changes",
                reloadedReview.getRating() == 2 && "Revised after the hackathon".equals(reloadedReview.getComment())
                        && !userNamed(reloaded, "mark_lee").isActive()
                        && userNamed(reloaded, "mark_lee").passwordMatches("user123"));
    }

    /**
     * Regression checks for the problems found in the code audit (F1 to F13 in the Q&A reviewer).
     * Each group uses its own freshly seeded data folder.
     */
    private static void testAuditFixes() {
        System.out.println("\n--- Testing Audit Fixes (Regression Checks) ---");
        LocalDateTime now = LocalDateTime.now();

        // F1: report data cannot be changed from outside ReportService
        ReportService.AdminDashboardData dash = new ReportService(freshStore("audit-reports")).getAdminDashboard();
        boolean mapLocked = false;
        try {
            dash.getCategoryPopularity().put("Made Up", 99);
        } catch (UnsupportedOperationException e) {
            mapLocked = true;
        }
        boolean listLocked = false;
        try {
            dash.getOrganizerStats().clear();
        } catch (UnsupportedOperationException e) {
            listLocked = true;
        }
        assertTrue("Dashboard maps and lists are read-only", mapLocked && listLocked);
        assertTrue("Report classes have no public fields, only getters",
                hasNoPublicFields(ReportService.AdminDashboardData.class)
                        && hasNoPublicFields(ReportService.OrganizerDashboardData.class)
                        && hasNoPublicFields(ReportService.EventPerformance.class));

        // F2: a save that fails is reported, and saves never leave temporary files behind
        DataStore saving = freshStore("audit-save");
        Path savingDir = Paths.get("test_data", "audit-save");
        User gavinSaving = userNamed(saving, "gavin_org");
        LocalDateTime slot = now.plusDays(45).withHour(10).withMinute(0);
        events(saving).createEvent(gavinSaving, "Saved Event", "", "Seminar", "Room S1", slot, slot.plusHours(2), 10);
        assertTrue("Saves write through a temporary file and leave none behind",
                !Files.exists(savingDir.resolve("events.json.tmp"))
                        && new DataStore(savingDir.toString()).getEventRepository().size() == 7);
        blockFile(savingDir.resolve("events.json"));
        boolean saveFailureReported = false;
        try {
            events(saving).createEvent(gavinSaving, "Unsaved Event", "", "Seminar", "Room S2",
                    slot.plusDays(1), slot.plusDays(1).plusHours(2), 10);
        } catch (DataSaveException e) {
            saveFailureReported = e.getMessage().contains("events.json");
        }
        assertTrue("A failed save is reported instead of a success message",
                saveFailureReported && !Files.exists(savingDir.resolve("events.json.tmp")));

        // F3: completed events are read-only; a started event keeps its start time
        DataStore editing = freshStore("audit-edit");
        EventService editingEvents = events(editing);
        User adminEditing = userNamed(editing, "admin");
        User gavin = userNamed(editing, "gavin_org");
        Event orientation = editing.getEventRepository().findById("EVT-1006").get();
        String orientationTitle = orientation.getTitle();
        EventService.EventResult editCompleted = editingEvents.updateEvent(adminEditing, "EVT-1006",
                "Renamed After It Ended", null, null, null, null, null, 0);
        assertTrue("A completed event cannot be edited",
                !editCompleted.success && orientation.getTitle().equals(orientationTitle));
        boolean modelRefused = false;
        try {
            orientation.updateDetails("Renamed After It Ended", orientation.getDescription(), orientation.getCategory(),
                    orientation.getVenue(), orientation.getStartDateTime(), orientation.getEndDateTime(), orientation.getCapacity());
        } catch (IllegalStateException e) {
            modelRefused = true;
        }
        assertTrue("The Event itself refuses edits once it is Completed", modelRefused);
        Event liveWorkshop = new Event("EVT-9002", "Live Workshop", "Running now", gavin.getUserId(), "Workshop",
                "Room L2", now.minusHours(1), now.plusHours(1), 10);
        editing.getEventRepository().add(liveWorkshop);
        EventService.EventResult moveStart = editingEvents.updateEvent(gavin, "EVT-9002", null, null, null, null,
                now.plusDays(1), now.plusDays(1).plusHours(2), 0);
        assertTrue("The start time cannot move once the event has started",
                !moveStart.success && moveStart.message.contains("started"));
        LocalDateTime laterEnd = liveWorkshop.getEndDateTime().plusMinutes(30);
        EventService.EventResult extendLive = editingEvents.updateEvent(gavin, "EVT-9002", "Live Workshop (Extended)",
                null, null, null, liveWorkshop.getStartDateTime(), laterEnd, 0);
        assertTrue("A started event can still be renamed and end later",
                extendLive.success && liveWorkshop.getEndDateTime().equals(laterEnd));
        Event pastSeminar = editing.getEventRepository().findById("EVT-1003").get();
        pastSeminar.updateDetails(pastSeminar.getTitle(), pastSeminar.getDescription(), pastSeminar.getCategory(),
                pastSeminar.getVenue(), now.minusDays(2), now.minusDays(2).plusHours(2), pastSeminar.getCapacity());
        EventService.EventResult retitlePast = editingEvents.updateEvent(gavin, "EVT-1003", "Design Systems (Recap)",
                null, null, null, pastSeminar.getStartDateTime(), pastSeminar.getEndDateTime(), 0);
        assertTrue("An event whose start has passed can be renamed without moving it", retitlePast.success);

        // F4: rescheduling must not double-book the people holding seats
        DataStore moving = freshStore("audit-reschedule");
        Event summit = moving.getEventRepository().findById("EVT-1001").get();
        Event lab = moving.getEventRepository().findById("EVT-1002").get();
        LocalDateTime labStart = lab.getStartDateTime();
        User courtney = userNamed(moving, "courtney_org");
        EventService.EventResult clash = events(moving).updateEvent(courtney, "EVT-1002", null, null, null, null,
                summit.getStartDateTime().plusHours(1), summit.getStartDateTime().plusHours(3), 0);
        assertTrue("Rescheduling is refused when it would double-book seat holders",
                !clash.success && clash.message.contains("3 attendee(s)") && lab.getStartDateTime().equals(labStart));
        EventService.EventResult freeSlot = events(moving).updateEvent(courtney, "EVT-1002", null, null, null, null,
                labStart.plusDays(1), lab.getEndDateTime().plusDays(1), 0);
        assertTrue("Rescheduling to a time with no clashes still works", freeSlot.success);

        // F6: a value that cannot be read stops loading instead of becoming a default
        assertTrue("A misspelled event status stops loading",
                refusesToLoad("audit-status", "events.json", "\"status\": \"COMPLETED\"", "\"status\": \"COMPLETE\""));
        assertTrue("An unknown role stops loading",
                refusesToLoad("audit-role", "users.json", "\"role\": \"ADMIN\"", "\"role\": \"ADMINISTRATOR\""));
        assertTrue("An unreadable account creation date stops loading",
                refusesToLoad("audit-user-date", "users.json", "\"createdAt\": \"[^\"]+\"", "\"createdAt\": \"yesterday\""));
        assertTrue("A missing event capacity stops loading",
                refusesToLoad("audit-capacity", "events.json", "\"capacity\": \\d+,", ""));

        // F7: category rules live in CategoryService
        DataStore cats = freshStore("audit-categories");
        CategoryService categories = new CategoryService(cats);
        User adminCats = userNamed(cats, "admin");
        assertTrue("Only Administrators can add categories",
                !categories.addCategory(userNamed(cats, "courtney_org"), "Esports", "Competitive gaming").success);
        assertTrue("Category names must be unique, ignoring letter case",
                !categories.addCategory(adminCats, "workshop", "Duplicate").success);
        assertTrue("A category that an event uses cannot be removed",
                !categories.removeCategory(adminCats, "CAT-101").success);
        OperationResult added = categories.addCategory(adminCats, "Esports", "Competitive gaming");
        String catsDir = Paths.get("test_data", "audit-categories").toString();
        boolean savedAfterAdd = new DataStore(catsDir).getCategoryRepository().existsByName("Esports");
        String esportsId = cats.getCategoryRepository().findByName("Esports").get().getCategoryId();
        OperationResult removed = categories.removeCategory(adminCats, esportsId);
        assertTrue("An unused category can be added and removed, and both changes are saved",
                added.success && savedAfterAdd && removed.success
                        && !new DataStore(catsDir).getCategoryRepository().existsByName("Esports"));

        // F8: one capacity limit everywhere
        DataStore limits = freshStore("audit-capacity-limit");
        LocalDateTime expo = now.plusDays(55).withHour(9).withMinute(0);
        EventService.EventResult tooBig = events(limits).createEvent(userNamed(limits, "gavin_org"), "Huge Expo", "",
                "Conference", "Stadium", expo, expo.plusHours(3), InputValidator.MAX_CAPACITY + 1);
        EventService.EventResult growTooBig = events(limits).updateEvent(userNamed(limits, "courtney_org"), "EVT-1001",
                null, null, null, null, null, null, InputValidator.MAX_CAPACITY + 1);
        assertTrue("Capacity above the limit is rejected when creating and when editing",
                !tooBig.success && tooBig.message.contains("50,000") && !growTooBig.success);

        // F11: seats taken = confirmed + checked in (EVT-1006: 2 checked in, 1 no-show)
        assertTrue("Seats taken counts confirmed and checked-in attendees",
                limits.getRegistrationRepository().countSeatsTakenByEvent("EVT-1006") == 3
                        && limits.getRegistrationRepository().countCheckedInByEvent("EVT-1006") == 2);

        // F13: utility classes cannot be instantiated
        assertTrue("Utility classes are final with only private constructors",
                isUtilityClass(ConsoleIO.class) && isUtilityClass(InputValidator.class) && isUtilityClass(SimpleJson.class));
    }

    /** An EventService wired the way MenuController wires it. RegistrationService holds no state, so a new one is fine. */
    private static EventService events(DataStore store) {
        return new EventService(store, new RegistrationService(store));
    }

    /** A UserService wired the way MenuController wires it. */
    private static UserService users(DataStore store) {
        return new UserService(store, new RegistrationService(store));
    }

    /** Seeds a data folder, edits one file with a regular expression, and checks that loading it is refused. */
    private static boolean refusesToLoad(String name, String file, String regex, String replacement) {
        freshStore(name);
        Path path = Paths.get("test_data", name, file);
        String original = readText(path);
        String edited = original.replaceFirst(regex, replacement);
        if (edited.equals(original)) {
            return false; // the edit did not apply, so the check would prove nothing
        }
        writeText(path, edited);
        try {
            new DataStore(Paths.get("test_data", name).toString());
            return false;
        } catch (DataLoadException e) {
            return e.getMessage().contains(file);
        }
    }

    /** Replaces a data file with a folder that is not empty, so the file can no longer be written. */
    private static void blockFile(Path file) {
        try {
            Files.delete(file);
            Files.createDirectory(file);
            Files.write(file.resolve("blocker.txt"), "blocks the save".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean hasNoPublicFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtilityClass(Class<?> type) {
        if (!Modifier.isFinal(type.getModifiers())) {
            return false;
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                return false;
            }
        }
        return true;
    }

    private static DataStore freshStore(String name) {
        File dir = new File("test_data", name);
        cleanDirectory(dir);
        return new DataStore(dir.getPath());
    }

    private static User userNamed(DataStore store, String username) {
        return store.getUserRepository().findByUsername(username).get();
    }

    private static String readText(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeText(Path path, String content) {
        try {
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void cleanDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        cleanDirectory(f);
                    } else {
                        f.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
