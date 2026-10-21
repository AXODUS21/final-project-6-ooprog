package plano.ui;

import plano.model.Event;
import plano.model.User;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.EventSortStrategy;
import plano.strategy.SortByDateAscStrategy;
import plano.util.ConsoleIO;
import plano.util.TableRenderer;

import java.util.List;

/**
 * Top-level application controller managing the landing screen,
 * authentication loop, and role-based menu routing.
 */
public class MenuController {
    private final DataStore dataStore;
    private final AuthService authService;
    private final UserService userService;
    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FeedbackService feedbackService;
    private final ReportService reportService;

    private final AdminMenu adminMenu;
    private final OrganizerMenu organizerMenu;
    private final AttendeeMenu attendeeMenu;

    public MenuController(DataStore dataStore) {
        this.dataStore = dataStore;
        this.authService = new AuthService(dataStore);
        this.userService = new UserService(dataStore);
        this.eventService = new EventService(dataStore);
        this.registrationService = new RegistrationService(dataStore);
        this.feedbackService = new FeedbackService(dataStore);
        this.reportService = new ReportService(dataStore);

        this.adminMenu = new AdminMenu(dataStore, authService, userService, eventService, registrationService, feedbackService, reportService);
        this.organizerMenu = new OrganizerMenu(dataStore, authService, eventService, registrationService, feedbackService, reportService);
        this.attendeeMenu = new AttendeeMenu(dataStore, authService, userService, eventService, registrationService, feedbackService);
    }

    public void start() {
        boolean running = true;
        while (running) {
            ConsoleIO.printBanner(
                    "PlanO - Event Management System",
                    "Welcome!!"
            );

            System.out.println(" 1. Log In to Account");
            System.out.println(" 2. Register New Account");
            System.out.println(" 3. Browse Public Events Directory");
            System.out.println(" 4. Demo Accounts & System Guide");
            System.out.println(" 0. Exit System");
            System.out.println();

            int choice = ConsoleIO.readInt("Select an option", 0, 4);
            switch (choice) {
                case 1:
                    handleLogin();
                    break;
                case 2:
                    handleRegister();
                    break;
                case 3:
                    browsePublicEvents();
                    break;
                case 4:
                    displayDemoCredentials();
                    break;
                case 0:
                    ConsoleIO.printSuccess("Thank you for using PlanO. Goodbye!");
                    dataStore.saveAll();
                    running = false;
                    break;
                default:
                    break;
            }
        }
    }

    private void handleLogin() {
        ConsoleIO.printSection("User Login");
        if (dataStore.getUserRepository().size() == 0) {
            ConsoleIO.printError("No accounts are registered in the database yet. Please register a new account first (Option 2).");
            ConsoleIO.pause();
            return;
        }

        String username = ConsoleIO.readNonEmptyString("Username (or '0' to return)");
        if (ConsoleIO.isCancelToken(username)) return;
        String password = ConsoleIO.readNonEmptyString("Password (or 'cancel' to return)");
        if (ConsoleIO.isCancelToken(password)) return;

        AuthService.LoginResult res = authService.login(username, password);
        if (res.success) {
            ConsoleIO.printSuccess(res.message);
            ConsoleIO.pause();
            routeUser(res.user);
        } else {
            ConsoleIO.printError(res.message);
            ConsoleIO.pause();
        }
    }

    private void routeUser(User user) {
        if (user.isAdmin()) {
            adminMenu.display();
        } else if (user.isOrganizer()) {
            organizerMenu.display();
        } else {
            attendeeMenu.display();
        }
    }

    private void handleRegister() {
        ConsoleIO.printSection("Create New PlanO Account");
        String username = ConsoleIO.readUsername("Choose Username");
        if (username == null) {
            ConsoleIO.printWarning("Registration cancelled.");
            ConsoleIO.pause();
            return;
        }
        String password = ConsoleIO.readPassword("Choose Password (6 - 64 chars, letters & digits)");
        if (password == null) {
            ConsoleIO.printWarning("Registration cancelled.");
            ConsoleIO.pause();
            return;
        }

        while (true) {
            String confirmPassword = ConsoleIO.readNonEmptyString("Confirm Password (or 'cancel' to abort)");
            if (ConsoleIO.isCancelToken(confirmPassword)) {
                ConsoleIO.printWarning("Registration cancelled.");
                ConsoleIO.pause();
                return;
            }
            if (password.equals(confirmPassword)) {
                break;
            }
            ConsoleIO.printError("Passwords do not match. Please re-enter your confirmation password.");
        }

        String fullName = ConsoleIO.readFullName("Your Full Name");
        if (fullName == null) {
            ConsoleIO.printWarning("Registration cancelled.");
            ConsoleIO.pause();
            return;
        }
        String email = ConsoleIO.readEmail("Your Email Address");
        if (email == null) {
            ConsoleIO.printWarning("Registration cancelled.");
            ConsoleIO.pause();
            return;
        }

        AuthService.RegisterResult res = authService.register(username, password, fullName, email);
        if (res.success) {
            ConsoleIO.printSuccess(res.message);
            ConsoleIO.printInfo("New accounts are Attendee accounts. An Administrator can promote you to Event Organizer.");
        } else {
            ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }

    private void browsePublicEvents() {
        ConsoleIO.printSection("Public Events Directory");
        List<Event> events = eventService.getAllEvents();
        EventSortStrategy strategy = new SortByDateAscStrategy();
        events = eventService.sortEvents(events, strategy);

        TableRenderer table = new TableRenderer(
                "ID", "Title", "Category", "Venue", "Start Time", "Capacity", "Confirmed", "Status"
        );

        for (Event e : events) {
            int confirmed = registrationService.getConfirmedCount(e.getEventId());
            String startStr = e.getStartDateTime() != null ? e.getStartDateTime().format(ConsoleIO.DATE_TIME_FORMATTER) : "N/A";
            table.addRow(
                    e.getEventId(),
                    e.getTitle(),
                    e.getCategory(),
                    e.getVenue(),
                    startStr,
                    String.valueOf(e.getCapacity()),
                    confirmed + "/" + e.getCapacity(),
                    e.getStatus().getDisplayName()
            );
        }
        table.render();
        System.out.println(ConsoleIO.CYAN + "Tip: Log in or register an account to RSVP or manage events!" + ConsoleIO.RESET);
        ConsoleIO.pause();
    }

    private void displayDemoCredentials() {
        ConsoleIO.printSection("PlanO Quick-Start Demo Credentials");
        System.out.println("The system comes pre-seeded with sample data to demonstrate all features:\n");

        TableRenderer table = new TableRenderer("Role", "Username", "Password", "Full Name", "Description");
        table.addRow("ADMIN", "admin", "admin123", "System Administrator", "Full oversight, users, categories, reports");
        table.addRow("ORGANIZER", "courtney_org", "org123", "Courtney Judge Fabello", "Hosts EVT-1001, 1002 (full), 1005, 1006");
        table.addRow("ORGANIZER", "gavin_org", "org123", "Gavin Hadrian Lodor", "Hosts EVT-1003, EVT-1004");
        table.addRow("ATTENDEE", "axellerosh", "user123", "Axellerosh Lubi", "EVT-1001, 1002; checked in at EVT-1006");
        table.addRow("ATTENDEE", "john_doe", "user123", "John Doe", "EVT-1001, 1002; checked in at EVT-1006");
        table.addRow("ATTENDEE", "jane_smith", "user123", "Jane Smith", "EVT-1001, 1002; no-show at EVT-1006");
        table.addRow("ATTENDEE", "mark_lee", "user123", "Mark Lee", "No registrations yet");
        table.addRow("ATTENDEE", "emily_watson", "user123", "Emily Watson", "Waitlist #1 on EVT-1002");
        table.render();

        System.out.println(ConsoleIO.BOLD + "Key Highlights to Test:" + ConsoleIO.RESET);
        System.out.println(" - " + ConsoleIO.BOLD + "Waitlist Auto-Promotion:" + ConsoleIO.RESET + " Log in as 'axellerosh' and cancel the EVT-1002 registration (REG-1004). 'emily_watson' is automatically promoted from the waitlist to Confirmed.");
        System.out.println(" - " + ConsoleIO.BOLD + "Conflict Detection:" + ConsoleIO.RESET + " Log in as an Organizer and try to create an event at 'Grand Auditorium A' during an overlapping time window.");
        System.out.println(" - " + ConsoleIO.BOLD + "Strategy Pattern:" + ConsoleIO.RESET + " Browse events and swap sorting by Popularity, Date, Alphabetical, or Capacity at runtime.");
        System.out.println(" - " + ConsoleIO.BOLD + "Attendance & Reviews:" + ConsoleIO.RESET + " Check in 'jane_smith' (USR-1006) for EVT-1006, view its reviews, or log in as 'axellerosh' to update a review. Only checked-in attendees can review.");
        System.out.println(" - " + ConsoleIO.BOLD + "Admin Dashboard:" + ConsoleIO.RESET + " Log in as 'admin' and view system-wide metrics, category popularity, and organizer leaderboards.");
        System.out.println(" - " + ConsoleIO.BOLD + "Persistence:" + ConsoleIO.RESET + " All changes are saved to JSON files in the 'data/' folder, which is created with this demo data on the first run.");

        ConsoleIO.pause();
    }
}
