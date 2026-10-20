package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.*;
import plano.util.ConsoleIO;
import plano.util.TableRenderer;

import java.util.List;
import java.util.Optional;

/**
 * Terminal interface for Regular Users (Attendees).
 * Provides event discovery, dynamic Strategy-based sorting, RSVP/waitlist registrations,
 * self-service cancellation with auto-promotion, and post-event reviews.
 */
public class AttendeeMenu {
    private final DataStore dataStore;
    private final AuthService authService;
    private final UserService userService;
    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FeedbackService feedbackService;

    private EventSortStrategy currentSortStrategy = new SortByDateAscStrategy();
    private final EventFilterCriteria activeFilterCriteria = new EventFilterCriteria();

    public AttendeeMenu(DataStore dataStore, AuthService authService, UserService userService,
                        EventService eventService, RegistrationService registrationService,
                        FeedbackService feedbackService) {
        this.dataStore = dataStore;
        this.authService = authService;
        this.userService = userService;
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.feedbackService = feedbackService;
    }

    public void display() {
        User user = authService.getCurrentUser();
        while (authService.isLoggedIn()) {
            ConsoleIO.printBanner("PlanO - Attendee Portal", "Welcome, " + user.getFullName() + " (" + user.getUsername() + ")");
            System.out.println(" 1. Discover & Browse Events (Sort: " + currentSortStrategy.getName() + ")");
            System.out.println(" 2. View Event Details & Register / RSVP");
            System.out.println(" 3. My Registrations (View Status, Waitlist & Cancel)");
            System.out.println(" 4. Submit Feedback & Rating for Completed Events");
            System.out.println(" 5. Switch Sorting Strategy / Filter Search");
            System.out.println(" 6. My Account Profile");
            System.out.println(" 0. Logout");
            System.out.println();

            int choice = ConsoleIO.readInt("Select an option", 0, 6);
            switch (choice) {
                case 1 -> browseEvents();
                case 2 -> viewDetailsAndRegister();
                case 3 -> manageMyRegistrations();
                case 4 -> submitEventFeedback();
                case 5 -> configureSortAndFilter();
                case 6 -> manageProfile();
                case 0 -> {
                    authService.logout();
                    ConsoleIO.printSuccess("Logged out successfully.");
                    return;
                }
            }
        }
    }

    private void browseEvents() {
        ConsoleIO.printSection("Discover Events");
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

    private void viewDetailsAndRegister() {
        ConsoleIO.printSection("Event Details & RSVP");
        if (eventService.getAllEvents().isEmpty()) {
            System.out.println("No events are currently scheduled in the system.");
            ConsoleIO.pause();
            return;
        }

        String eventId = ConsoleIO.readEventId("Enter Event ID (e.g. EVT-1002)");
        Optional<Event> eventOpt = eventService.getEventById(eventId);
        if (eventOpt.isEmpty()) {
            ConsoleIO.printError("Event not found with ID: " + eventId);
            ConsoleIO.pause();
            return;
        }

        Event ev = eventOpt.get();
        int confirmed = registrationService.getConfirmedCount(eventId);
        int waitlisted = registrationService.getWaitlistCount(eventId);
        int spotsLeft = Math.max(0, ev.getCapacity() - confirmed);

        System.out.println(ConsoleIO.BOLD + ConsoleIO.CYAN + "═".repeat(60) + ConsoleIO.RESET);
        System.out.println(ConsoleIO.BOLD + ev.getTitle() + " (" + ev.getEventId() + ")" + ConsoleIO.RESET);
        System.out.println("Category:    " + ev.getCategory());
        System.out.println("Venue:       " + ev.getVenue());
        System.out.println("Date & Time: " + (ev.getStartDateTime() != null ? ev.getStartDateTime().format(ConsoleIO.DISPLAY_FORMATTER) : "N/A") +
                " to " + (ev.getEndDateTime() != null ? ev.getEndDateTime().format(ConsoleIO.DISPLAY_FORMATTER) : "N/A"));
        System.out.println("Capacity:    " + confirmed + " / " + ev.getCapacity() + " filled (" + spotsLeft + " spots remaining)");
        if (waitlisted > 0) {
            System.out.println("Waitlist:    " + waitlisted + " attendees currently waiting");
        }
        System.out.println("Status:      " + ev.getStatus().getDisplayName());
        System.out.println("\nDescription:\n  " + ev.getDescription());
        System.out.println(ConsoleIO.BOLD + ConsoleIO.CYAN + "═".repeat(60) + ConsoleIO.RESET);

        User user = authService.getCurrentUser();
        Optional<Registration> userReg = dataStore.getRegistrationRepository().findByEventAndUser(eventId, user.getUserId());
        if (userReg.isPresent()) {
            System.out.println(ConsoleIO.YELLOW + "Your current registration status: " + userReg.get().getStatus().getDisplayName() + ConsoleIO.RESET);
            if (userReg.get().isWaitlisted()) {
                int pos = registrationService.getWaitlistPosition(eventId, user.getUserId());
                System.out.println("You are at Waitlist position #" + pos);
            }
        } else {
            if (ConsoleIO.readConfirmation("Would you like to register for this event?")) {
                RegistrationService.RegistrationResult res = registrationService.register(user, eventId);
                if (res.success) {
                    ConsoleIO.printSuccess(res.message);
                } else {
                    ConsoleIO.printError(res.message);
                }
            }
        }
        ConsoleIO.pause();
    }

    private void manageMyRegistrations() {
        ConsoleIO.printSection("My Event Registrations");
        User user = authService.getCurrentUser();
        List<Registration> myRegs = registrationService.getRegistrationsForUser(user.getUserId());

        if (myRegs.isEmpty()) {
            System.out.println("You have not registered for any events yet.");
            ConsoleIO.pause();
            return;
        }

        TableRenderer table = new TableRenderer("Reg ID", "Event ID", "Event Title", "Date", "Status", "Waitlist Pos");
        for (Registration r : myRegs) {
            Optional<Event> evOpt = eventService.getEventById(r.getEventId());
            String title = evOpt.map(Event::getTitle).orElse("Unknown Event");
            String dateStr = evOpt.flatMap(e -> Optional.ofNullable(e.getStartDateTime()))
                    .map(d -> d.format(ConsoleIO.DATE_TIME_FORMATTER)).orElse("N/A");
            String waitPos = r.isWaitlisted() ? "#" + registrationService.getWaitlistPosition(r.getEventId(), user.getUserId()) : "-";
            table.addRow(r.getRegistrationId(), r.getEventId(), title, dateStr, r.getStatus().getDisplayName(), waitPos);
        }
        table.render();

        System.out.println("1. Cancel / Withdraw a Registration");
        System.out.println("0. Return");
        int ch = ConsoleIO.readInt("Option", 0, 1);
        if (ch == 1) {
            String regId = ConsoleIO.readRegistrationId("Enter Registration ID to cancel (e.g. REG-1004)");
            if (ConsoleIO.readConfirmation("Are you sure you want to cancel this registration?")) {
                RegistrationService.CancellationResult res = registrationService.cancelRegistration(user, regId);
                if (res.success) {
                    ConsoleIO.printSuccess(res.message);
                } else {
                    ConsoleIO.printError(res.message);
                }
            }
        }
        ConsoleIO.pause();
    }

    private void submitEventFeedback() {
        ConsoleIO.printSection("Rate & Review Completed Events");
        User user = authService.getCurrentUser();
        List<Registration> myRegs = registrationService.getRegistrationsForUser(user.getUserId());

        boolean hasCompleted = false;
        TableRenderer table = new TableRenderer("Event ID", "Title", "Venue", "My Status", "My Rating");
        for (Registration r : myRegs) {
            Optional<Event> evOpt = eventService.getEventById(r.getEventId());
            if (evOpt.isPresent() && evOpt.get().isCompleted()) {
                hasCompleted = true;
                Event ev = evOpt.get();
                Optional<Feedback> fb = feedbackService.getUserFeedbackForEvent(ev.getEventId(), user.getUserId());
                String ratingStr = fb.map(f -> f.getStarsDisplay() + " (" + f.getRating() + "/5)").orElse("Not submitted");
                table.addRow(ev.getEventId(), ev.getTitle(), ev.getVenue(), r.getStatus().getDisplayName(), ratingStr);
            }
        }

        if (!hasCompleted) {
            System.out.println("You have no attended events that have been marked as completed yet.");
            ConsoleIO.pause();
            return;
        }

        table.render();
        System.out.println("1. Submit / Update Review");
        System.out.println("0. Return");
        int ch = ConsoleIO.readInt("Option", 0, 1);
        if (ch == 1) {
            String eventId = ConsoleIO.readEventId("Enter Event ID to review");
            int rating = ConsoleIO.readInt("Rating (1 = Poor, 5 = Excellent)", 1, 5);
            String comment = ConsoleIO.readComment("Your Review Comments");

            FeedbackService.FeedbackResult res = feedbackService.submitFeedback(user, eventId, rating, comment);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        }
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
        System.out.println("6. Set Keyword Search / Category / Venue Filter");
        System.out.println("7. Show Open Spots Only (" + (activeFilterCriteria.isAvailableOnly() ? "Enabled" : "Disabled") + ")");
        System.out.println("8. Clear All Active Filters");
        System.out.println("0. Back");

        int choice = ConsoleIO.readInt("Choose option", 0, 8);
        switch (choice) {
            case 1 -> {
                currentSortStrategy = new SortByDateAscStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
            }
            case 2 -> {
                currentSortStrategy = new SortByDateDescStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
            }
            case 3 -> {
                currentSortStrategy = new SortByPopularityStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
            }
            case 4 -> {
                currentSortStrategy = new SortByTitleStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
            }
            case 5 -> {
                currentSortStrategy = new SortByCapacityStrategy();
                ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
            }
            case 6 -> {
                String kw = ConsoleIO.readString("Search keyword (title/description, or leave blank)");
                activeFilterCriteria.setTextQuery(kw);
                String cat = ConsoleIO.readString("Category filter (or leave blank)");
                activeFilterCriteria.setCategory(cat);
                String venue = ConsoleIO.readString("Venue filter (or leave blank)");
                activeFilterCriteria.setVenue(venue);
                ConsoleIO.printSuccess("Filters applied.");
            }
            case 7 -> {
                activeFilterCriteria.setAvailableOnly(!activeFilterCriteria.isAvailableOnly());
                ConsoleIO.printSuccess("Open spots only filter set to: " + activeFilterCriteria.isAvailableOnly());
            }
            case 8 -> {
                activeFilterCriteria.reset();
                ConsoleIO.printSuccess("All filters cleared.");
            }
        }
        ConsoleIO.pause();
    }

    private void manageProfile() {
        ConsoleIO.printSection("My Account Profile");
        User user = authService.getCurrentUser();
        System.out.println("User ID:   " + user.getUserId());
        System.out.println("Username:  " + user.getUsername());
        System.out.println("Full Name: " + user.getFullName());
        System.out.println("Email:     " + user.getEmail());
        System.out.println("Role:      " + user.getRole().getDisplayName());
        System.out.println();

        if (ConsoleIO.readConfirmation("Would you like to update your profile details?")) {
            String newName = ConsoleIO.readOptionalString("New Full Name", user.getFullName());
            String newEmail = ConsoleIO.readOptionalEmail("New Email", user.getEmail());
            String newPass = ConsoleIO.readString("New Password (leave empty to keep current)");

            UserService.OperationResult res = userService.updateProfile(user.getUserId(), newName, newEmail, newPass);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }
}
