package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.strategy.*;
import plano.util.ConsoleIO;
import plano.util.TableRenderer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Terminal interface for Event Organizers.
 * Allows managing own events, attendee check-ins, performance analytics,
 * and conflict-free scheduling.
 */

public class OrganizerMenu {
    private final DataStore dataStore;
    private final AuthService authService;
    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FeedbackService feedbackService;
    private final ReportService reportService;

    private EventSortStrategy currentSortStrategy = new SortByDateAscStrategy();

    public OrganizerMenu(DataStore dataStore, AuthService authService, EventService eventService,
                         RegistrationService registrationService, FeedbackService feedbackService,
                         ReportService reportService) {
        this.dataStore = dataStore;
        this.authService = authService;
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.feedbackService = feedbackService;
        this.reportService = reportService;
    }

    public void display() {
        User organizer = authService.getCurrentUser();
        while (authService.isLoggedIn() && (organizer.isOrganizer() || organizer.isAdmin())) {
            ConsoleIO.printBanner("PlanO - Organizer Dashboard", "Logged in as: " + organizer.getFullName() + " (" + organizer.getUsername() + ")");
            System.out.println(" 1. Create New Event (with Conflict Detection)");
            System.out.println(" 2. View & Manage My Events (Edit, Status, Cancel)");
            System.out.println(" 3. Event Attendee Lists & Check-Ins");
            System.out.println(" 4. View Post-Event Feedback & Ratings");
            System.out.println(" 5. My Organizer Performance Analytics");
            System.out.println(" 6. Browse All Public Events");
            System.out.println(" 0. Logout");
            System.out.println();

            int choice = ConsoleIO.readInt("Select an option", 0, 6);
            switch (choice) {
                case 1 -> createEvent();
                case 2 -> manageMyEvents();
                case 3 -> manageCheckIns();
                case 4 -> viewMyEventFeedback();
                case 5 -> viewAnalytics();
                case 6 -> browseAllEvents();
                case 0 -> {
                    authService.logout();
                    ConsoleIO.printSuccess("Logged out successfully.");
                    return;
                }
            }
        }
    }

    private void createEvent() {
        ConsoleIO.printSection("Create New Event");
        String title = ConsoleIO.readCancellableEventTitle("Event Title");
        if (title == null) {
            ConsoleIO.printWarning("Event creation cancelled.");
            ConsoleIO.pause();
            return;
        }
        String description = ConsoleIO.readDescription("Description", 1000);

        List<Category> categories = dataStore.getCategoryRepository().findAll();
        System.out.println(ConsoleIO.CYAN + "Available Categories:" + ConsoleIO.RESET);
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + categories.get(i).getName());
        }
        int catIdx = ConsoleIO.readInt("Choose Category", 1, categories.size());
        String category = categories.get(catIdx - 1).getName();

        String venue = ConsoleIO.readVenue("Venue / Room");
        LocalDateTime start = ConsoleIO.readDateTime("Start Date & Time", true);
        LocalDateTime end = ConsoleIO.readDateTimeAfter("End Date & Time", start);
        int capacity = ConsoleIO.readPositiveInt("Attendee Capacity Limit");

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

    private void manageMyEvents() {
        ConsoleIO.printSection("My Organized Events");
        User user = authService.getCurrentUser();
        List<Event> myEvents = eventService.getEventsByOrganizer(user.getUserId());

        if (myEvents.isEmpty()) {
            System.out.println("You haven't created any events yet.");
            ConsoleIO.pause();
            return;
        }

        renderEventsTable(myEvents);

        System.out.println("1. Edit an Event");
        System.out.println("2. Update Event Status (Upcoming -> Ongoing -> Completed)");
        System.out.println("3. Cancel an Event");
        System.out.println("0. Return");

        int ch = ConsoleIO.readInt("Select", 0, 3);
        if (ch == 1) {
            String eventId = ConsoleIO.readEventId("Enter Event ID to edit");
            Optional<Event> evOpt = eventService.getEventById(eventId);
            if (evOpt.isPresent() && evOpt.get().getOrganizerId().equals(user.getUserId())) {
                Event ev = evOpt.get();
                if (ev.isCancelled()) {
                    ConsoleIO.printError("Cannot edit a cancelled event.");
                    ConsoleIO.pause();
                    return;
                }
                String title = ConsoleIO.readOptionalString("New Title", ev.getTitle());
                String desc = ConsoleIO.readOptionalString("New Description", ev.getDescription());
                String venue = ConsoleIO.readOptionalString("New Venue", ev.getVenue());
                LocalDateTime start = ConsoleIO.readOptionalDateTime("New Start Time", ev.getStartDateTime(), ev.isUpcoming());
                LocalDateTime end = ConsoleIO.readOptionalDateTimeAfter("New End Time", ev.getEndDateTime(), start);
                int cap = ConsoleIO.readOptionalPositiveInt("New Capacity", ev.getCapacity());

                EventService.EventResult res = eventService.updateEvent(user, eventId, title, desc, ev.getCategory(),
                        venue, start, end, cap, ev.getStatus());
                if (res.success) ConsoleIO.printSuccess(res.message);
                else ConsoleIO.printError(res.message);
            } else {
                ConsoleIO.printError("Event not found or you are not the organizer.");
            }
        } else if (ch == 2) {
            String eventId = ConsoleIO.readEventId("Enter Event ID to change status");
            System.out.println("1. UPCOMING | 2. ONGOING | 3. COMPLETED");
            int statOpt = ConsoleIO.readInt("Select Status", 1, 3);
            EventStatus st = statOpt == 1 ? EventStatus.UPCOMING : (statOpt == 2 ? EventStatus.ONGOING : EventStatus.COMPLETED);
            EventService.EventResult res = eventService.updateEventStatus(user, eventId, st);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        } else if (ch == 3) {
            String eventId = ConsoleIO.readEventId("Enter Event ID to cancel");
            if (ConsoleIO.readConfirmation("Are you sure you want to cancel this event?")) {
                EventService.EventResult res = eventService.cancelEvent(user, eventId);
                if (res.success) ConsoleIO.printSuccess(res.message);
                else ConsoleIO.printError(res.message);
            }
        }
        ConsoleIO.pause();
    }

    private void manageCheckIns() {
        ConsoleIO.printSection("My Event Attendee Check-Ins");
        User user = authService.getCurrentUser();
        List<Event> myEvents = eventService.getEventsByOrganizer(user.getUserId());

        if (myEvents.isEmpty()) {
            System.out.println("No events found under your account.");
            ConsoleIO.pause();
            return;
        }

        renderEventsTable(myEvents);
        String eventId = ConsoleIO.readEventId("Enter Event ID to view attendees & check in");
        Optional<Event> evOpt = eventService.getEventById(eventId);
        if (evOpt.isEmpty() || !evOpt.get().getOrganizerId().equals(user.getUserId())) {
            ConsoleIO.printError("Event not found or unauthorized.");
            ConsoleIO.pause();
            return;
        }

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
            RegistrationService.CheckInResult res = registrationService.checkInAttendee(user, eventId, attendeeUserId);
            if (res.success) ConsoleIO.printSuccess(res.message);
            else ConsoleIO.printError(res.message);
        }
        ConsoleIO.pause();
    }

    private void viewMyEventFeedback() {
        ConsoleIO.printSection("Reviews for My Completed Events");
        User user = authService.getCurrentUser();
        List<Event> myEvents = eventService.getEventsByOrganizer(user.getUserId());

        for (Event e : myEvents) {
            List<Feedback> fbs = feedbackService.getFeedbackForEvent(e.getEventId());
            if (!fbs.isEmpty()) {
                double avg = feedbackService.getAverageRating(e.getEventId());
                System.out.println(ConsoleIO.BOLD + "Event: " + e.getTitle() + " (" + e.getEventId() + ")" + ConsoleIO.RESET);
                System.out.printf("Average Rating: %.2f / 5.00 (%d reviews)\n", avg, fbs.size());

                TableRenderer table = new TableRenderer("Feedback ID", "User", "Rating", "Review", "Date");
                for (Feedback fb : fbs) {
                    Optional<User> u = dataStore.getUserRepository().findById(fb.getUserId());
                    String name = u.map(User::getFullName).orElse(fb.getUserId());
                    String dt = fb.getSubmittedAt() != null ? fb.getSubmittedAt().format(ConsoleIO.DATE_TIME_FORMATTER) : "-";
                    table.addRow(fb.getFeedbackId(), name, fb.getStarsDisplay() + " (" + fb.getRating() + ")", fb.getComment(), dt);
                }
                table.render();
                System.out.println();
            }
        }
        ConsoleIO.pause();
    }

    private void viewAnalytics() {
        ConsoleIO.printSection("My Organizer Analytics Dashboard");
        User user = authService.getCurrentUser();
        ReportService.OrganizerDashboardData data = reportService.getOrganizerDashboard(user.getUserId());

        System.out.println(ConsoleIO.BOLD + "📈 SUMMARY METRICS" + ConsoleIO.RESET);
        System.out.printf("  Total Events Created: %d  (Upcoming: %d | Completed: %d | Cancelled: %d)\n",
                data.totalEvents, data.upcomingEvents, data.completedEvents, data.cancelledEvents);
        System.out.printf("  Total Confirmed Attendees: %d | Total Waitlisted: %d | Total Checked-In: %d\n\n",
                data.totalRegistrations, data.totalWaitlisted, data.totalCheckedIn);

        TableRenderer table = new TableRenderer(
                "Event ID", "Title", "Status", "Cap", "Confirmed", "Waitlist", "Checked-in", "Fill Rate", "Check-in %", "Avg Rating"
        );

        for (ReportService.EventPerformance p : data.eventPerformances) {
            String avgRatingStr = p.reviewCount > 0 ? String.format("%.1f ★ (%d)", p.averageRating, p.reviewCount) : "No reviews";
            table.addRow(
                    p.eventId,
                    p.title,
                    p.status.getDisplayName(),
                    String.valueOf(p.capacity),
                    String.valueOf(p.confirmed),
                    String.valueOf(p.waitlisted),
                    String.valueOf(p.checkedIn),
                    String.format("%.1f%%", p.getFillRate()),
                    String.format("%.1f%%", p.getAttendanceRate()),
                    avgRatingStr
            );
        }
        table.render();
        ConsoleIO.pause();
    }

    private void browseAllEvents() {
        ConsoleIO.printSection("All Events Across System");
        List<Event> events = eventService.getAllEvents();
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
}
