package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.EventFilterCriteria;
import plano.util.ConsoleIO;
import plano.util.InputValidator;
import plano.util.TableRenderer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Administrative terminal interface with full system oversight, user management,
 * category configuration, and analytical dashboards. Administrators may manage any event.
 */
public class AdminMenu extends EventManagerMenu {
    private final UserService userService;
    private final ReportService reportService;
    private final EventFilterCriteria activeFilterCriteria = new EventFilterCriteria();

    public AdminMenu(DataStore dataStore, AuthService authService, UserService userService,
                     EventService eventService, RegistrationService registrationService,
                     FeedbackService feedbackService, ReportService reportService) {
        super(dataStore, authService, eventService, registrationService, feedbackService);
        this.userService = userService;
        this.reportService = reportService;
    }

    @Override
    protected String getPortalTitle() {
        return "PlanO - Administrator Portal";
    }

    @Override
    protected void printMenuOptions() {
        System.out.println(" 1. Browse & Search All Events (Current Sort: " + currentSortStrategy.getName() + ")");
        System.out.println(" 2. Create New Event (System-wide)");
        System.out.println(" 3. Manage Any Event (Edit, Cancel, Change Status)");
        System.out.println(" 4. View Attendee List & Perform Check-Ins");
        System.out.println(" 5. View Event Reviews & Feedback");
        System.out.println(" 6. User Management (View, Deactivate/Reactivate, Promote)");
        System.out.println(" 7. Event Category Management");
        System.out.println(" 8. System Reports & Analytical Dashboard");
        System.out.println(" 9. Change Sorting Strategy / Filters");
    }

    @Override
    protected int getOptionCount() {
        return 9;
    }

    @Override
    protected void handleOption(int choice) {
        switch (choice) {
            case 1:
                browseEvents("System Events Directory", activeFilterCriteria);
                break;
            case 2:
                createEvent("Create New Event (Admin)");
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
            default:
                break;
        }
    }

    /** Administrators have oversight of every event. */
    @Override
    protected boolean canManage(Event event) {
        return true;
    }

    private void manageEvent() {
        ConsoleIO.printSection("Manage Event (Admin Oversight)");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system to manage.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID to manage (e.g. EVT-1001)");
        Optional<Event> eventOpt = findManageableEvent(eventId);
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
                advanceEventStatus(event);
                break;
            case 3:
                cancelEventAfterConfirmation(event);
                break;
            default:
                break;
        }
        ConsoleIO.pause();
    }

    private void manageCheckIns() {
        ConsoleIO.printSection("Event Attendance & Check-In");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID (e.g. EVT-1001)");
        Optional<Event> eventOpt = findManageableEvent(eventId);
        if (!eventOpt.isPresent()) {
            ConsoleIO.printError("Event not found with ID: " + eventId);
            ConsoleIO.pause();
            return;
        }
        showRosterAndCheckIn(eventOpt.get());
    }

    private void viewEventFeedback() {
        ConsoleIO.printSection("Event Reviews & Post-Event Feedback");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events found in the system.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID (e.g. EVT-1001)");
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
            UserService.OperationResult res = userService.deactivateUser(authService.getCurrentUser(), uid);
            printResult(res.success, res.message);
        } else if (ch == 2) {
            String uid = ConsoleIO.readUserId("Enter User ID to reactivate (e.g. USR-1004)");
            UserService.OperationResult res = userService.reactivateUser(authService.getCurrentUser(), uid);
            printResult(res.success, res.message);
        } else if (ch == 3) {
            String uid = ConsoleIO.readUserId("Enter User ID to promote to Organizer (e.g. USR-1004)");
            UserService.OperationResult res = userService.promoteToOrganizer(authService.getCurrentUser(), uid);
            printResult(res.success, res.message);
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
        printSortOptions();
        System.out.println("6. Set Keyword Search / Category Filter");
        System.out.println("7. Clear All Active Filters");
        System.out.println("0. Back");

        int choice = ConsoleIO.readInt("Choose option", 0, 7);
        if (!applySortChoice(choice)) {
            if (choice == 6) {
                String kw = ConsoleIO.readString("Search keyword (title/description/venue, or leave blank)");
                activeFilterCriteria.setTextQuery(kw);
                String cat = ConsoleIO.readString("Category filter (or leave blank)");
                activeFilterCriteria.setCategory(cat);
                ConsoleIO.printSuccess("Filters applied.");
            } else if (choice == 7) {
                activeFilterCriteria.reset();
                ConsoleIO.printSuccess("All filters cleared.");
            }
        }
        ConsoleIO.pause();
    }
}
