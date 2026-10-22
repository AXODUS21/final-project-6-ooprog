package plano.ui;

import plano.model.Event;
import plano.model.User;
import plano.repository.DataSaveException;
import plano.repository.DataStore;
import plano.service.AuthService;
import plano.service.EventService;
import plano.service.FeedbackService;
import plano.service.RegistrationService;
import plano.strategy.*;
import plano.util.ConsoleIO;
import plano.util.OperationCancelledException;
import plano.util.TableRenderer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base class for the three role menus (Administrator, Organizer, Attendee).
 *
 * {@link #display()} is a template method: it runs the menu loop every role shares
 * (banner, options, logout, cancelled operations) and calls the abstract methods
 * below for the parts that differ by role. The menu controller holds each role's
 * menu as a RoleMenu and calls display() without knowing which role it is.
 */
public abstract class RoleMenu {
    /**
     * The sort orders a user can pick, in menu order (Strategy pattern). The sort menu is built
     * from this list, so a new sort order is one new EventSortStrategy class plus one line here.
     * The strategies hold no state, so every menu can share these objects.
     */
    private static final List<EventSortStrategy> SORT_STRATEGIES = Collections.unmodifiableList(Arrays.asList(
            new SortByDateAscStrategy(),
            new SortByDateDescStrategy(),
            new SortByPopularityStrategy(),
            new SortByTitleStrategy(),
            new SortByCapacityStrategy()));

    protected final DataStore dataStore;
    protected final AuthService authService;
    protected final EventService eventService;
    protected final RegistrationService registrationService;
    protected final FeedbackService feedbackService;

    /** Sort order used when this role browses events (Strategy pattern). */
    protected EventSortStrategy currentSortStrategy = SORT_STRATEGIES.get(0);

    protected RoleMenu(DataStore dataStore, AuthService authService, EventService eventService,
                       RegistrationService registrationService, FeedbackService feedbackService) {
        this.dataStore = dataStore;
        this.authService = authService;
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.feedbackService = feedbackService;
    }

    /**
     * Shows this role's menu until the user logs out. A cancelled operation, or a change
     * that could not be saved, returns here with a message instead of ending the session.
     */
    public final void display() {
        while (authService.isLoggedIn()) {
            User user = authService.getCurrentUser();
            ConsoleIO.printBanner(getPortalTitle(), getBannerSubtitle(user));
            printMenuOptions();
            System.out.println(" 0. Logout");
            System.out.println();

            int choice = ConsoleIO.readInt("Select an option", 0, getOptionCount());
            if (choice == 0) {
                authService.logout();
                ConsoleIO.printSuccess("Logged out successfully.");
                return;
            }
            try {
                handleOption(choice);
            } catch (OperationCancelledException cancelled) {
                ConsoleIO.printWarning("Operation cancelled. Returning to the menu.");
                ConsoleIO.pause();
            } catch (DataSaveException saveFailed) {
                ConsoleIO.printError(saveFailed.getMessage());
                ConsoleIO.pause();
            }
        }
    }

    // ==================== PARTS EACH ROLE PROVIDES ====================

    protected abstract String getPortalTitle();

    protected String getBannerSubtitle(User user) {
        return "Logged in as: " + user.getFullName() + " (" + user.getUsername() + ")";
    }

    /** Prints options 1 to {@link #getOptionCount()}; "0. Logout" is added by display(). */
    protected abstract void printMenuOptions();

    protected abstract int getOptionCount();

    /** Runs the option the user picked (1 to {@link #getOptionCount()}). */
    protected abstract void handleOption(int choice);

    // ==================== SHARED SCREENS AND HELPERS ====================

    /**
     * Lists all events, filtered by the given criteria (or unfiltered when null)
     * and sorted by the current sort strategy.
     */
    protected void browseEvents(String sectionTitle, EventFilterCriteria criteria) {
        ConsoleIO.printSection(sectionTitle);
        List<Event> events = eventService.getAllEvents();
        if (criteria != null) {
            events = eventService.filterEvents(events, criteria);
        }
        events = eventService.sortEvents(events, currentSortStrategy);
        renderEventsTable(events);
        ConsoleIO.pause();
    }

    protected void renderEventsTable(List<Event> events) {
        TableRenderer table = new TableRenderer(
                "ID", "Title", "Category", "Venue", "Start Time", "Cap", "Confirmed", "Waitlist", "Status"
        );

        for (Event e : events) {
            int confirmed = registrationService.getSeatsTaken(e.getEventId());
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

    /**
     * Prints the header and one numbered choice per sort strategy (1 to {@link #sortOptionCount()}),
     * using each strategy's own name and description.
     */
    protected void printSortOptions() {
        ConsoleIO.printSection("Configure Search, Filter & Strategy Sorting");
        System.out.println("Active Sort Strategy: " + ConsoleIO.BOLD + currentSortStrategy.getName() + ConsoleIO.RESET);
        for (int i = 0; i < SORT_STRATEGIES.size(); i++) {
            EventSortStrategy strategy = SORT_STRATEGIES.get(i);
            System.out.println((i + 1) + ". " + strategy.getName() + " - " + strategy.getDescription());
        }
    }

    /** How many sort choices {@link #printSortOptions()} prints; a screen numbers its own options after these. */
    protected int sortOptionCount() {
        return SORT_STRATEGIES.size();
    }

    /**
     * Switches to the sort strategy for a choice from 1 to {@link #sortOptionCount()}.
     *
     * @return false if the choice is not one of the sort choices
     */
    protected boolean applySortChoice(int choice) {
        if (choice < 1 || choice > SORT_STRATEGIES.size()) {
            return false;
        }
        currentSortStrategy = SORT_STRATEGIES.get(choice - 1);
        ConsoleIO.printSuccess("Swapped sorting strategy to: " + currentSortStrategy.getName());
        return true;
    }

    /** Prints a service result as a success or an error. */
    protected static void printResult(boolean success, String message) {
        if (success) {
            ConsoleIO.printSuccess(message);
        } else {
            ConsoleIO.printError(message);
        }
    }
}
