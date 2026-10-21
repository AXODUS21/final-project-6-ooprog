package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.*;
import plano.util.ConsoleIO;
import plano.util.InputValidator;
import plano.util.TableRenderer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Administrative terminal interface with full system oversight, user management,
 * category configuration, and analytical dashboards.
 */
public class AdminMenu {
    private final DataStore dataStore;
    private final AuthService authService;
    private final UserService userService;
    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FeedbackService feedbackService;
    private final ReportService reportService;

    private EventSortStrategy currentSortStrategy = new SortByDateAscStrategy();
    private final EventFilterCriteria activeFilterCriteria = new EventFilterCriteria();

    public AdminMenu(DataStore dataStore, AuthService authService, UserService userService,
                     EventService eventService, RegistrationService registrationService,
                     FeedbackService feedbackService, ReportService reportService) {
        this.dataStore = dataStore;
        this.authService = authService;
        this.userService = userService;
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.feedbackService = feedbackService;
        this.reportService = reportService;
    }

    public void display() {
        User admin = authService.getCurrentUser();
        while (authService.isLoggedIn() && admin.isAdmin()) {
            ConsoleIO.printBanner("PlanO - Administrator Portal", "Logged in as: " + admin.getFullName() + " (" + admin.getUsername() + ")");
            System.out.println(" 1. Browse & Search All Events (Current Sort: " + currentSortStrategy.getName() + ")");
            System.out.println(" 2. Create New Event (System-wide)");
            System.out.println(" 3. Manage Any Event (Edit, Cancel, Change Status)");
            System.out.println(" 4. View Attendee List & Perform Check-Ins");
            System.out.println(" 5. View Event Reviews & Feedback");
            System.out.println(" 6. User Management (View, Deactivate/Reactivate, Promote)");
            System.out.println(" 7. Event Category Management");
            System.out.println(" 8. System Reports & Analytical Dashboard");
            System.out.println(" 9. Change Sorting Strategy / Filters");
            System.out.println(" 0. Logout");
            System.out.println();

            int choice = ConsoleIO.readInt("Select an option", 0, 9);
            switch (choice) {
                case 1:
                    browseEvents();
                    break;
                case 2:
                    createEvent();
                    break;
                case 3:
                    manageEvent();
                    break;
                case 4:
                    manageCheckIns();
                    break;
                case 5:
                    viewEventFeedback();
                    break;
                case 6:
                    manageUsers();
                    break;
                case 7:
                    manageCategories();
                    break;
                case 8:
                    viewDashboard();
                    break;
                case 9:
                    configureSortAndFilter();
                    break;
                case 0:
                    authService.logout();
                    ConsoleIO.printSuccess("Logged out successfully.");
                    return;
                default:
                    break;
            }
        }
    }

    private void browseEvents() {
        ConsoleIO.printSection("System Events Directory");
        List<Event> events = eventService.getAllEvents();
        events = eventService.filterEvents(events, activeFilterCriteria);
        events = eventService.sortEvents(events, currentSortStrategy);

        renderEventsTable(events);
        ConsoleIO.pause();
    }

    private void renderEventsTable(List<Event> events) {
        TableRenderer table = new TableRenderer(
                "ID", "Title", "Category", "Venue", "Start Time", "Cap", "Confirmed", "Waitlist", "Status"
        );

        for (Event e : events) {
            int confirmed = registrationService.getConfirmedCount(e.getEventId());
            int waitlisted = registrationService.getWaitlistCount(e.getEventId());
            String capStr = confirmed + "/" + e.getCapacity();
            String startStr = e.getStartDateTime() != null ? e.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) : "N/A";

            table.addRow(
                    e.getEventId(),
                    e.getTitle(),
                    e.getCategory(),
                    e.getVenue(),
                    startStr,
                    String.valueOf(e.getCapacity()),
                    capStr,
                    String.valueOf(waitlisted),
                    e.getStatus().getDisplayName()
            );
        }
        table.render();
    }

    private void createEvent() {
        ConsoleIO.printSection("Create New Event (Admin)");
        String title = ConsoleIO.readCancellableEventTitle("Event Title");
        if (title == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        String description = ConsoleIO.readDescription("Description", 1000);

        // Category selection
        List<Category> categories = dataStore.getCategoryRepository().findAll();
        System.out.println(ConsoleIO.CYAN + "Available Categories:" + ConsoleIO.RESET);
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + categories.get(i).getName());
        }
        System.out.println("  0. Cancel");
        int catIdx = ConsoleIO.readInt("Choose Category", 0, categories.size());
        if (catIdx == 0) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        String category = categories.get(catIdx - 1).getName();

        String venue = ConsoleIO.readVenue("Venue / Room");
        if (venue == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        LocalDateTime start = ConsoleIO.readDateTime("Start Date & Time", true);
        if (start == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        LocalDateTime end = ConsoleIO.readDateTimeAfter("End Date & Time", start);
        if (end == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        Integer capacity = ConsoleIO.readCancellablePositiveInt("Attendee Capacity Limit");
        if (capacity == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }

        EventService.EventResult res = eventService.createEvent(
                authService.getCurrentUser(), title, description, category, venue, start, end, capacity
        );

        if (res.success) {
            ConsoleIO.printSuccess(res.message);
        } else {
            ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }

    private void manageEvent() {
        ConsoleIO.printSection("Manage Event (Admin Oversight)");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system to manage.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID to manage (e.g. EVT-1001)");
        if (eventId == null) return;
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (!eventOpt.isPresent()) {
            ConsoleIO.printError("Event not found with ID: " + eventId);
            ConsoleIO.pause();
            return;
        }

        Event event = eventOpt.get();
        System.out.println("Selected: " + ConsoleIO.BOLD + event.getTitle() + " (" + event.getEventId() + ")" + ConsoleIO.RESET);
        System.out.println("Current Status: " + event.getStatus().getDisplayName() + " | Venue: " + event.getVenue());
        System.out.println("1. Edit Event Details");
        System.out.println("2. Advance Event Status (Ongoing / Completed)");
        System.out.println("3. Cancel Event");
        System.out.println("0. Back");

        int choice = ConsoleIO.readInt("Action", 0, 3);
        switch (choice) {
            case 1:
                editEventDetails(event);
                break;
            case 2:
                changeEventStatus(event);
                break;
            case 3:
                if (ConsoleIO.readConfirmation("Are you sure you want to cancel event '" + event.getTitle() + "'?")) {
                    EventService.EventResult res = eventService.cancelEvent(authService.getCurrentUser(), eventId);
                    if (res.success) ConsoleIO.printSuccess(res.message);
                    else ConsoleIO.printError(res.message);
                }
                break;
            default:
                break;
        }
        ConsoleIO.pause();
    }

    private void editEventDetails(Event event) {
        ConsoleIO.printSection("Edit Event: " + event.getTitle());
        if (event.isCancelled()) {
            ConsoleIO.printError("Cannot edit a cancelled event.");
            return;
        }

        String title = ConsoleIO.readOptionalString("New Title", event.getTitle());
        String desc = ConsoleIO.readOptionalString("New Description", event.getDescription());
        String venue = ConsoleIO.readOptionalString("New Venue", event.getVenue());
        LocalDateTime start = ConsoleIO.readOptionalDateTime("New Start Time", event.getStartDateTime(), event.isUpcoming());
        LocalDateTime end = ConsoleIO.readOptionalDateTimeAfter("New End Time", event.getEndDateTime(), start);
        int cap = ConsoleIO.readOptionalPositiveInt("New Capacity Limit", event.getCapacity());

        EventService.EventResult res = eventService.updateEvent(
                authService.getCurrentUser(), event.getEventId(), title, desc, event.getCategory(),
                venue, start, end, cap
        );

        if (res.success) ConsoleIO.printSuccess(res.message);
        else ConsoleIO.printError(res.message);
    }

    private void changeEventStatus(Event event) {
        System.out.println("Current Status: " + event.getStatus().getDisplayName() + " (statuses only move forward)");
        System.out.println("Select New Status:");
        System.out.println(" 1. ONGOING");
        System.out.println(" 2. COMPLETED");
        System.out.println(" 0. Back");
        int opt = ConsoleIO.readInt("Choice", 0, 2);
        if (opt == 0) {
            return;
        }
        EventStatus newStatus = opt == 1 ? EventStatus.ONGOING : EventStatus.COMPLETED;

        EventService.EventResult res = eventService.updateEventStatus(authService.getCurrentUser(), event.getEventId(), newStatus);
        if (res.success) ConsoleIO.printSuccess(res.message);
        else ConsoleIO.printError(res.message);
    }

    private void manageCheckIns() {
        ConsoleIO.printSection("Event Attendance & Check-In");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID (e.g. EVT-1001)");
        if (eventId == null) return;
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (!eventOpt.isPresent()) {
            ConsoleIO.printError("Event not found with ID: " + eventId);
            ConsoleIO.pause();
            return;
        }

        Event event = eventOpt.get();
        List<Registration> regs = registrationService.getRegistrationsForEvent(eventId);

        TableRenderer table = new TableRenderer("Reg ID", "User ID", "Name", "Email", "Status", "Checked-in Time");
        for (Registration r : regs) {
            Optional<User> uOpt = dataStore.getUserRepository().findById(r.getUserId());
            String name = uOpt.map(User::getFullName).orElse("Unknown");
            String email = uOpt.map(User::getEmail).orElse("Unknown");
            String checkInStr = r.getCheckedInAt() != null ? r.getCheckedInAt().format(ConsoleIO.DATE_TIME_FORMATTER) : "-";
            table.addRow(r.getRegistrationId(), r.getUserId(), name, email, r.getStatus().getDisplayName(), checkInStr);
        }
        table.render();

        if (regs.isEmpty()) {
            System.out.println(ConsoleIO.YELLOW + "No attendees have registered for this event yet. Check-in is not available." + ConsoleIO.RESET);
            ConsoleIO.pause();
            return;
        }

        System.out.println("1. Check In an Attendee");
        System.out.println("0. Return");
        int ch = ConsoleIO.readInt("Select", 0, 1);
        if (ch == 1) {
            String attendeeUserId = ConsoleIO.readUserId("Enter Attendee User ID (e.g. USR-1004)");
            if (attendeeUserId == null) return;
            RegistrationService.CheckInResult res = registrationService.checkInAttendee(authService.getCurrentUser(), eventId, attendeeUserId);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }

    private void viewEventFeedback() {
        ConsoleIO.printSection("Event Reviews & Post-Event Feedback");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID (e.g. EVT-1001)");
        if (eventId == null) return;
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (!eventOpt.isPresent()) {
            ConsoleIO.printError("Event not found with ID: " + eventId);
            ConsoleIO.pause();
            return;
        }

        List<Feedback> feedbacks = feedbackService.getFeedbackForEvent(eventId);
        double avg = feedbackService.getAverageRating(eventId);

        System.out.println(ConsoleIO.BOLD + "Event: " + eventOpt.get().getTitle() + ConsoleIO.RESET);
        System.out.printf("Average Rating: %.2f / 5.00 (%d reviews)\n\n", avg, feedbacks.size());

        TableRenderer table = new TableRenderer("Feedback ID", "User", "Rating", "Review Comment", "Date");
        for (Feedback fb : feedbacks) {
            Optional<User> u = dataStore.getUserRepository().findById(fb.getUserId());
            String userName = u.map(User::getFullName).orElse(fb.getUserId());
            String dt = fb.getSubmittedAt() != null ? fb.getSubmittedAt().format(ConsoleIO.DATE_TIME_FORMATTER) : "-";
            table.addRow(fb.getFeedbackId(), userName, fb.getStarsDisplay() + " (" + fb.getRating() + ")", fb.getComment(), dt);
        }
        table.render();
        ConsoleIO.pause();
    }

    private void manageUsers() {
        ConsoleIO.printSection("User Accounts Management");
        List<User> users = userService.getAllUsers();
        if (users.isEmpty()) {
            System.out.println("No user accounts found in the system.");
            ConsoleIO.pause();
            return;
        }

        TableRenderer table = new TableRenderer("User ID", "Username", "Full Name", "Email", "Role", "Active?");
        for (User u : users) {
            table.addRow(u.getUserId(), u.getUsername(), u.getFullName(), u.getEmail(),
                    u.getRole().getDisplayName(), u.isActive() ? "Active" : "Deactivated");
        }
        table.render();

        System.out.println("1. Deactivate a User Account");
        System.out.println("2. Reactivate a User Account");
        System.out.println("3. Promote an Attendee to Event Organizer");
        System.out.println("0. Back");

        int ch = ConsoleIO.readInt("Option", 0, 3);
        if (ch == 1) {
            String uid = ConsoleIO.readUserId("Enter User ID to deactivate (e.g. USR-1004)");
            if (uid == null) return;
            UserService.OperationResult res = userService.deactivateUser(authService.getCurrentUser(), uid);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        } else if (ch == 2) {
            String uid = ConsoleIO.readUserId("Enter User ID to reactivate (e.g. USR-1004)");
            if (uid == null) return;
            UserService.OperationResult res = userService.reactivateUser(authService.getCurrentUser(), uid);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        } else if (ch == 3) {
            String uid = ConsoleIO.readUserId("Enter User ID to promote to Organizer (e.g. USR-1004)");
            if (uid == null) return;
            UserService.OperationResult res = userService.promoteToOrganizer(authService.getCurrentUser(), uid);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }

    private void manageCategories() {
        ConsoleIO.printSection("Event Categories");
        List<Category> categories = dataStore.getCategoryRepository().findAll();

        TableRenderer table = new TableRenderer("Category ID", "Name", "Description");
        for (Category c : categories) {
            table.addRow(c.getCategoryId(), c.getName(), c.getDescription());
        }
        table.render();

        System.out.println("1. Add New Category");
        System.out.println("2. Remove a Category");
        System.out.println("0. Back");

        int ch = ConsoleIO.readInt("Option", 0, 2);
        if (ch == 1) {
            String name = ConsoleIO.readNonEmptyString("Category Name");
            if (!InputValidator.isValidCategoryName(name)) {
                ConsoleIO.printError("Category name must be between 2 and 50 characters long.");
            } else if (dataStore.getCategoryRepository().existsByName(name.trim())) {
                ConsoleIO.printError("Category '" + name.trim() + "' already exists.");
            } else {
                String desc = ConsoleIO.readString("Category Description");
                String id = dataStore.nextCategoryId();
                dataStore.getCategoryRepository().add(new Category(id, name.trim(), desc));
                dataStore.saveCategories();
                ConsoleIO.printSuccess("Category '" + name.trim() + "' added successfully.");
            }
        } else if (ch == 2) {
            if (categories.isEmpty()) {
                ConsoleIO.printError("No categories exist to remove.");
                ConsoleIO.pause();
                return;
            }
            String id = ConsoleIO.readCategoryId("Enter Category ID to remove (e.g. CAT-101)");
            if (id == null) return;
            Optional<Category> catOpt = dataStore.getCategoryRepository().findById(id);
            if (!catOpt.isPresent()) {
                ConsoleIO.printError("Category not found with ID: " + id);
            } else {
                Category cat = catOpt.get();
                long assignedEventsCount = dataStore.getEventRepository().findAll().stream()
                        .filter(e -> cat.getName().equalsIgnoreCase(e.getCategory()))
                        .count();
                if (assignedEventsCount > 0) {
                    ConsoleIO.printError("Cannot delete category '" + cat.getName() + "': " +
                            assignedEventsCount + " event(s) are currently assigned to it.");
                } else {
                    dataStore.getCategoryRepository().delete(id);
                    dataStore.saveCategories();
                    ConsoleIO.printSuccess("Category '" + cat.getName() + "' removed successfully.");
                }
            }
        }
        ConsoleIO.pause();
    }

    private void viewDashboard() {
        ConsoleIO.printSection("System-Wide Analytical Reports & Dashboard");
        ReportService.AdminDashboardData dash = reportService.getAdminDashboard();

        System.out.println(ConsoleIO.BOLD + "SYSTEM METRICS OVERVIEW" + ConsoleIO.RESET);
        System.out.printf("  Total Events: %d  (Upcoming: %d | Ongoing: %d | Completed: %d | Cancelled: %d | Ended, not marked completed: %d)\n",
                dash.totalEvents, dash.upcomingEvents, dash.ongoingEvents, dash.completedEvents,
                dash.cancelledEvents, dash.awaitingCompletionEvents);
        System.out.printf("  Registered Users: %d  (Organizers: %d | Attendees: %d)\n",
                dash.totalUsers, dash.totalOrganizers, dash.totalAttendees);
        System.out.printf("  Total Active Registrations: %d  (Confirmed: %d | Checked-in: %d | Waitlisted: %d)\n",
                dash.totalRegistrations, dash.confirmedRegistrations, dash.checkedInAttendees, dash.waitlistedRegistrations);
        System.out.printf("  Attendance Rate (completed events): %.1f%%  (%d of %d registered attendees checked in)\n\n",
                dash.overallAttendanceRate, dash.completedEventCheckIns, dash.completedEventRegistrations);

        System.out.println(ConsoleIO.BOLD + "CATEGORY POPULARITY (Seats taken, cancelled events excluded)" + ConsoleIO.RESET);
        TableRenderer catTable = new TableRenderer("Category", "Registrations Hosted");
        for (Map.Entry<String, Integer> entry : dash.categoryPopularity.entrySet()) {
            catTable.addRow(entry.getKey(), String.valueOf(entry.getValue()));
        }
        catTable.render();

        System.out.println(ConsoleIO.BOLD + "ORGANIZER ACTIVITY LEADERBOARD" + ConsoleIO.RESET);
        TableRenderer orgTable = new TableRenderer("Organizer", "Username", "Events Created", "Total Registrations", "Checked-in Attendees");
        for (ReportService.OrganizerStats s : dash.organizerStats) {
            orgTable.addRow(s.getFullName(), s.getUsername(), String.valueOf(s.getEventCount()),
                    String.valueOf(s.getTotalRegistrations()), String.valueOf(s.getTotalCheckedIn()));
        }
        orgTable.render();

        ConsoleIO.pause();
    }

    private void configureSortAndFilter() {
        ConsoleIO.printSection("Configure Search, Filter & Strategy Sorting");
        System.out.println("Active Sort Strategy: " + ConsoleIO.BOLD + currentSortStrategy.getName() + ConsoleIO.RESET);
        System.out.println("1. Date (Earliest First)");
        System.out.println("2. Date (Latest First)");
        System.out.println("3. Popularity (Most Registrations First)");
        System.out.println("4. Alphabetical (Title A - Z)");
        System.out.println("5. Capacity (Largest Venue First)");
        System.out.println("6. Set Keyword Search / Category Filter");
        System.out.println("7. Clear All Active Filters");
        System.out.println("0. Back");

        int choice = ConsoleIO.readInt("Choose option", 0, 7);
        switch (choice) {
            case 1:
                currentSortStrategy = new SortByDateAscStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
                break;
            case 2:
                currentSortStrategy = new SortByDateDescStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
                break;
            case 3:
                currentSortStrategy = new SortByPopularityStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
                break;
            case 4:
                currentSortStrategy = new SortByTitleStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
                break;
            case 5:
                currentSortStrategy = new SortByCapacityStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
                break;
            case 6:
                String kw = ConsoleIO.readString("Search keyword (title/description/venue, or leave blank)");
                activeFilterCriteria.setTextQuery(kw);
                String cat = ConsoleIO.readString("Category filter (or leave blank)");
                activeFilterCriteria.setCategory(cat);
                ConsoleIO.printSuccess("Filters applied.");
                break;
            case 7:
                activeFilterCriteria.reset();
                ConsoleIO.printSuccess("All filters cleared.");
                break;
            default:
                break;
        }
        ConsoleIO.pause();
    }
}
