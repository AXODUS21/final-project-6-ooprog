package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.*;
import plano.util.ConsoleIO;
import plano.util.TableRenderer;

import java.util.List;
import java.util.Optional;

/**
 * Terminal interface for Event Organizers.
 * Allows managing their own events, attendee check-ins, performance analytics,
 * and conflict-free scheduling. Organizers may only manage the events they created.
 */
public class OrganizerMenu extends EventManagerMenu {
    private final ReportService reportService;

    public OrganizerMenu(DataStore dataStore, AuthService authService, EventService eventService,
                         RegistrationService registrationService, FeedbackService feedbackService,
                         ReportService reportService) {
        super(dataStore, authService, eventService, registrationService, feedbackService);
        this.reportService = reportService;
    }

    @Override
    protected String getPortalTitle() {
        return "PlanO - Organizer Dashboard";
    }

    @Override
    protected void printMenuOptions() {
        System.out.println(" 1. Create New Event (with Conflict Detection)");
        System.out.println(" 2. View & Manage My Events (Edit, Status, Cancel)");
        System.out.println(" 3. Event Attendee Lists & Check-Ins");
        System.out.println(" 4. View Post-Event Feedback & Ratings");
        System.out.println(" 5. My Organizer Performance Analytics");
        System.out.println(" 6. Browse All Public Events");
    }

    @Override
    protected int getOptionCount() {
        return 6;
    }

    @Override
    protected void handleOption(int choice) {
        switch (choice) {
            case 1:
                createEvent("Create New Event");
                break;
            case 2:
                manageMyEvents();
                break;
            case 3:
                manageCheckIns();
                break;
            case 4:
                viewMyEventFeedback();
                break;
            case 5:
                viewAnalytics();
                break;
            case 6:
                browseEvents("All Events Across System", null);
                break;
            default:
                break;
        }
    }

    /** Organizers may only manage events they created. */
    @Override
    protected boolean canManage(Event event) {
        return event.getOrganizerId().equals(authService.getCurrentUser().getUserId());
    }

    private List<Event> myEvents() {
        return eventService.getEventsByOrganizer(authService.getCurrentUser().getUserId());
    }

    private void manageMyEvents() {
        ConsoleIO.printSection("My Organized Events");
        List<Event> myEvents = myEvents();

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
        if (ch != 0) {
            String prompt = ch == 1 ? "Enter Event ID to edit"
                    : ch == 2 ? "Enter Event ID to change status"
                    : "Enter Event ID to cancel";
            String eventId = ConsoleIO.readEventId(prompt);
            Optional<Event> evOpt = findManageableEvent(eventId);
            if (!evOpt.isPresent()) {
                ConsoleIO.printError("Event not found or you are not the organizer.");
            } else if (ch == 1) {
                editEventDetails(evOpt.get());
            } else if (ch == 2) {
                advanceEventStatus(evOpt.get());
            } else {
                cancelEventAfterConfirmation(evOpt.get());
            }
        }
        ConsoleIO.pause();
    }

    private void manageCheckIns() {
        ConsoleIO.printSection("My Event Attendee Check-Ins");
        List<Event> myEvents = myEvents();

        if (myEvents.isEmpty()) {
            System.out.println("No events found under your account.");
            ConsoleIO.pause();
            return;
        }

        renderEventsTable(myEvents);
        String eventId = ConsoleIO.readEventId("Enter Event ID to view attendees & check in");
        Optional<Event> evOpt = findManageableEvent(eventId);
        if (!evOpt.isPresent()) {
            ConsoleIO.printError("Event not found or unauthorized.");
            ConsoleIO.pause();
            return;
        }
        showRosterAndCheckIn(evOpt.get());
    }

    private void viewMyEventFeedback() {
        ConsoleIO.printSection("Reviews for My Completed Events");

        for (Event e : myEvents()) {
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

        System.out.println(ConsoleIO.BOLD + "SUMMARY METRICS" + ConsoleIO.RESET);
        System.out.printf("  Total Events Created: %d  (Upcoming: %d | Ongoing: %d | Completed: %d | Cancelled: %d | Ended, not marked completed: %d)\n",
                data.getTotalEvents(), data.getUpcomingEvents(), data.getOngoingEvents(), data.getCompletedEvents(),
                data.getCancelledEvents(), data.getAwaitingCompletionEvents());
        System.out.printf("  Total Confirmed Attendees: %d | Total Waitlisted: %d | Total Checked-In: %d\n\n",
                data.getTotalRegistrations(), data.getTotalWaitlisted(), data.getTotalCheckedIn());
        if (data.getAwaitingCompletionEvents() > 0) {
            ConsoleIO.printWarning(data.getAwaitingCompletionEvents() + " of your events have ended but are not marked Completed. " +
                    "Mark them Completed (My Events > Update Event Status) so attendees can leave reviews.");
            System.out.println();
        }

        TableRenderer table = new TableRenderer(
                "Event ID", "Title", "Status", "Cap", "Confirmed", "Waitlist", "Checked-in", "Fill Rate", "Check-in %", "Avg Rating"
        );

        for (ReportService.EventPerformance p : data.getEventPerformances()) {
            String avgRatingStr = p.getReviewCount() > 0
                    ? String.format("%.1f/5 (%d)", p.getAverageRating(), p.getReviewCount())
                    : "No reviews";
            table.addRow(
                    p.getEventId(),
                    p.getTitle(),
                    p.getStatus().getDisplayName(),
                    String.valueOf(p.getCapacity()),
                    String.valueOf(p.getSeatsTaken()),
                    String.valueOf(p.getWaitlisted()),
                    String.valueOf(p.getCheckedIn()),
                    String.format("%.1f%%", p.getFillRate()),
                    String.format("%.1f%%", p.getAttendanceRate()),
                    avgRatingStr
            );
        }
        table.render();
        ConsoleIO.pause();
    }
}
