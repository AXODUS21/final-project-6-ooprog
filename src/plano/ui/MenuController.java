package plano.ui;

import plano.model.Event;
import plano.model.Role;
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
                case 1 -> handleLogin();
                case 2 -> handleRegister();
                case 3 -> browsePublicEvents();
                case 4 -> displayDemoCredentials();
                case 0 -> {
                    ConsoleIO.printSuccess("Thank you for using PlanO. Goodbye!");
                    dataStore.saveAll();
                    running = false;
                }
            }
        }
    }

    private void handleLogin() {
        ConsoleIO.printSection("User Login");
        String username = ConsoleIO.readNonEmptyString("Username");
        String password = ConsoleIO.readNonEmptyString("Password");

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
        String username = ConsoleIO.readNonEmptyString("Choose Username");
        String password = ConsoleIO.readNonEmptyString("Choose Password (min 4 chars)");
        String fullName = ConsoleIO.readNonEmptyString("Your Full Name");
        String email = ConsoleIO.readEmail("Your Email Address");

        System.out.println("Select Account Type:");
        System.out.println("  1. Regular User / Attendee (Discover & RSVP to events)");
        System.out.println("  2. Event Organizer (Create & host events)");
        int roleOpt = ConsoleIO.readInt("Role Choice", 1, 2);
        Role role = roleOpt == 2 ? Role.ORGANIZER : Role.ATTENDEE;

        AuthService.RegisterResult res = authService.register(username, password, fullName, email, role);
        if (res.success) {
            ConsoleIO.printSuccess(res.message);
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
        table.addRow("ADMIN", "admin", "admin123", "System Administrator", "Full oversight, user management, and analytics");
        table.addRow("ORGANIZER", "courtney_org", "org123", "Courtney Judge Fabello", "Host of EVT-1001, EVT-1002 (at capacity), EVT-1006");
        table.addRow("ORGANIZER", "gavin_org", "org123", "Gavin Hadrian Lodor", "Host of EVT-1003, EVT-1004");
        table.addRow("ATTENDEE", "axellerosh", "user123", "Axellerosh Lubi", "Registered for EVT-1001, EVT-1002, EVT-1006");
        table.addRow("ATTENDEE", "emily_watson", "user123", "Emily Watson", "On the waitlist for EVT-1002 (cancel one spot to auto-promote!)");
        table.render();

        System.out.println(ConsoleIO.BOLD + "Key Highlights to Test:" + ConsoleIO.RESET);
        System.out.println(" • " + ConsoleIO.BOLD + "Waitlist Auto-Promotion:" + ConsoleIO.RESET + " Log in as 'axellerosh', cancel EVT-1002 registration. Notice how 'emily_watson' is automatically promoted from Waitlist to Confirmed!");
        System.out.println(" • " + ConsoleIO.BOLD + "Conflict Detection:" + ConsoleIO.RESET + " Log in as Organizer, try to create an event at 'Grand Auditorium A' during an overlapping time window.");
        System.out.println(" • " + ConsoleIO.BOLD + "Strategy Pattern:" + ConsoleIO.RESET + " Browse events and swap sorting by Popularity, Date, Alphabetical, or Capacity at runtime.");
        System.out.println(" • " + ConsoleIO.BOLD + "Attendance & Reviews:" + ConsoleIO.RESET + " Check in attendees for EVT-1006, view post-event 5-star feedback, or submit a new review.");
        System.out.println(" • " + ConsoleIO.BOLD + "Admin Dashboard:" + ConsoleIO.RESET + " Log in as 'admin' and view system-wide KPI metrics, category popularity, and organizer leaderboards.");
        System.out.println(" • " + ConsoleIO.BOLD + "Persistence:" + ConsoleIO.RESET + " All state is saved to JSON files in the 'data/' directory between runs.");

        ConsoleIO.pause();
    }
}
