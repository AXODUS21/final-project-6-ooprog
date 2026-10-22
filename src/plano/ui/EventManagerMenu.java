package plano.ui;

import plano.model.*;
import plano.repository.DataStore;
import plano.service.AuthService;
import plano.service.EventService;
import plano.service.FeedbackService;
import plano.service.RegistrationService;
import plano.util.ConsoleIO;
import plano.util.OperationCancelledException;
import plano.util.TableRenderer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Screens shared by the roles that run events: Administrators and Organizers.
 * The only rule that differs between them is which events they may manage,
 * which each subclass defines in {@link #canManage(Event)}.
 */
public abstract class EventManagerMenu extends RoleMenu {

    protected EventManagerMenu(DataStore dataStore, AuthService authService, EventService eventService,
                               RegistrationService registrationService, FeedbackService feedbackService) {
        super(dataStore, authService, eventService, registrationService, feedbackService);
    }

    /** Administrators may manage any event; Organizers only the events they created. */
    protected abstract boolean canManage(Event event);

    /** Returns the event with this ID if it exists and this role may manage it. */
    protected Optional<Event> findManageableEvent(String eventId) {
        Optional<Event> event = eventService.getEventById(eventId);
        return event.isPresent() && canManage(event.get()) ? event : Optional.empty();
    }

    /** Asks for every detail of a new event, then creates it. */
    protected void createEvent(String sectionTitle) {
        ConsoleIO.printSection(sectionTitle);
        String title = ConsoleIO.readCancellableEventTitle("Event Title");
        String description = ConsoleIO.readDescription("Description", 1000);

        List<Category> categories = dataStore.getCategoryRepository().findAll();
        System.out.println(ConsoleIO.CYAN + "Available Categories:" + ConsoleIO.RESET);
        for (int i = 0; i < categories.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + categories.get(i).getName());
        }
        System.out.println("  0. Cancel");
        int catIdx = ConsoleIO.readInt("Choose Category", 0, categories.size());
        if (catIdx == 0) {
            throw new OperationCancelledException();
        }
        String category = categories.get(catIdx - 1).getName();

        String venue = ConsoleIO.readVenue("Venue / Room");
        LocalDateTime start = ConsoleIO.readDateTime("Start Date & Time", true);
        LocalDateTime end = ConsoleIO.readDateTimeAfter("End Date & Time", start);
        int capacity = ConsoleIO.readCancellablePositiveInt("Attendee Capacity Limit");

        EventService.EventResult res = eventService.createEvent(
                authService.getCurrentUser(), title, description, category, venue, start, end, capacity
        );
        printResult(res.success, res.message);
        ConsoleIO.pause();
    }

    /**
     * Asks for new details (blank keeps the current value) and saves them.
     * Completed and cancelled events are refused before any prompt, and the start time
     * is not asked for once the event has started.
     */
    protected void editEventDetails(Event event) {
        ConsoleIO.printSection("Edit Event: " + event.getTitle());
        try {
            event.checkEditable();
        } catch (IllegalStateException notEditable) {
            ConsoleIO.printError(notEditable.getMessage());
            return;
        }

        String title = ConsoleIO.readOptionalString("New Title", event.getTitle());
        String desc = ConsoleIO.readOptionalString("New Description", event.getDescription());
        String venue = ConsoleIO.readOptionalString("New Venue", event.getVenue());
        LocalDateTime start = event.getStartDateTime();
        if (event.isOngoing() || event.hasStarted(LocalDateTime.now())) {
            ConsoleIO.printInfo("The start time (" + start.format(ConsoleIO.DATE_TIME_FORMATTER) +
                    ") stays the same because the event has started.");
        } else {
            start = ConsoleIO.readOptionalDateTime("New Start Time", event.getStartDateTime(), true);
        }
        LocalDateTime end = ConsoleIO.readOptionalDateTimeAfter("New End Time", event.getEndDateTime(), start);
        int cap = ConsoleIO.readOptionalPositiveInt("New Capacity Limit", event.getCapacity());

        EventService.EventResult res = eventService.updateEvent(
                authService.getCurrentUser(), event.getEventId(), title, desc, event.getCategory(),
                venue, start, end, cap
        );
        printResult(res.success, res.message);
    }

    /** Moves an event forward to Ongoing or Completed. */
    protected void advanceEventStatus(Event event) {
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
        printResult(res.success, res.message);
    }

    protected void cancelEventAfterConfirmation(Event event) {
        if (ConsoleIO.readConfirmation("Are you sure you want to cancel event '" + event.getTitle() + "'?")) {
            EventService.EventResult res = eventService.cancelEvent(authService.getCurrentUser(), event.getEventId());
            printResult(res.success, res.message);
        }
    }

    /** Shows an event's attendee list and lets the user check an attendee in. */
    protected void showRosterAndCheckIn(Event event) {
        List<Registration> regs = registrationService.getRegistrationsForEvent(event.getEventId());

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
            RegistrationService.CheckInResult res = registrationService.checkInAttendee(
                    authService.getCurrentUser(), event.getEventId(), attendeeUserId);
            printResult(res.success, res.message);
        }
        ConsoleIO.pause();
    }
}
