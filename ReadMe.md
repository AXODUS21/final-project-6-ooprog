# PlanO - Event Management System

**Group #:** Group 6  
**Section:** C2B  
**Course:** Object-Oriented Programming (OOPROG)  
**Group Members:**
- Axellerosh F. Lubi
- Courtney Judge P. Fabello
- Gavin Hadrian M. Lodor

---

## I. Project Overview

**PlanO** is a Java-based Event Management System built with pure Java SE (Standard Library, zero external dependencies). It runs on **Java 8 or newer**. It provides organizations, academic clubs, and communities with a centralized platform to organize, register for, and track events.

PlanO eliminates error-prone coordination methods (spreadsheets, group chats, paper sign-up sheets) through built-in capacity management, venue scheduling conflict detection, automated FIFO waitlists with instant promotions, attendance check-ins, post-event feedback ratings, and administrative dashboards.

---

## II. Key Features & Functionalities

### 1. Role-Based Access Control (RBAC)
PlanO separates administrative governance from day-to-day event operations across three distinct roles:
- **Administrator (`ADMIN`):**
  - Full system oversight: view, edit, or cancel any event across the entire system.
  - User management: view all user accounts, deactivate/reactivate users, and promote attendees to organizers.
  - Category management: add or remove event categories (a category used by an event cannot be removed).
  - System-wide analytical reports: attendance rate, category popularity, and organizer leaderboards.
- **Event Organizer (`ORGANIZER`):**
  - Create, update, and cancel their **own** events, and move them forward to Ongoing and Completed.
  - Manage attendee rosters and check attendees in from the event day onward.
  - View scoped analytics, fill rates, attendance percentages, and post-event feedback ratings for their own events.
- **Regular User / Attendee (`ATTENDEE`):**
  - Sign up for an account (new accounts are Attendees; an Administrator can promote them to Organizer).
  - Discover and search events with live filter and sorting strategies.
  - Register for events with automatic capacity validation and waitlist placement.
  - Manage personal registrations and withdraw from events (the freed seat goes to the waitlist).
  - Submit 1-to-5 star reviews for completed events they were checked in at.
  - Manage personal profile details (name, email, password).

---

### 2. Design Patterns & Architecture
- **Strategy Pattern (`plano.strategy`):**
  - Used for dynamic event sorting and multi-criteria filtering at runtime.
  - Interfaces: `EventSortStrategy` and `EventFilterStrategy`.
  - Concrete Sort Strategies:
    - `SortByDateAscStrategy`: Chronological order (earliest first).
    - `SortByDateDescStrategy`: Reverse-chronological order (latest first).
    - `SortByPopularityStrategy`: Ranks events by number of confirmed registrations descending.
    - `SortByTitleStrategy`: Alphabetical order (A to Z).
    - `SortByCapacityStrategy`: Ranks events by venue capacity descending.
  - Attendees and admins can swap sorting strategies dynamically from the console UI.
- **Template Method Pattern (`plano.ui`):**
  - `RoleMenu.display()` runs the menu loop every role shares and calls the steps each role menu supplies (title, options, actions).
- **Repository Pattern (`plano.repository`):**
  - Decouples in-memory domain collections from persistence storage.
  - `UserRepository`, `EventRepository`, `RegistrationRepository`, `FeedbackRepository`, `CategoryRepository`.
- **Lifecycle owned by the model (`Event`, `Registration`):**
  - `Event.cancel()` and `Event.changeStatus(...)` decide which status changes are allowed; `Registration.promote()`, `checkIn(...)` and `cancel()` do the same for registrations. Services call these methods instead of setting statuses directly.
- **Pure Java JSON Persistence (`DataStore` & `SimpleJson`):**
  - Implements a pure standard Java recursive-descent JSON parser and serializer.
  - All data is saved automatically to human-readable JSON files in the `data/` directory (`users.json`, `events.json`, `registrations.json`, `feedbacks.json`, `categories.json`).
  - The `data/` directory is created on the first run with demo data dated relative to that day, so the demo events are always upcoming. It is not stored in git.
  - Loading is all-or-nothing: if a file is malformed, PlanO stops with a message naming the file instead of starting with partial data.
- **Scheduling Conflict Detection (`EventService`):**
  - Detects overlapping time windows at the same venue for non-cancelled events (`start1 < end2 && end1 > start2`).
  - Flags conflicts with exact conflicting event IDs, titles, and time intervals to prevent double-booking.
- **Capacity & FIFO Waitlist Auto-Promotion (`RegistrationService`):**
  - When an event hits maximum capacity, registrants are automatically placed on a FIFO waitlist with an assigned queue number.
  - Whenever a seat opens, the system immediately promotes the next attendee in the waitlist queue to `CONFIRMED`.

---

### 3. Business Rules

| Area | Rule |
|---|---|
| Venue conflicts | Two non-cancelled events at the same venue cannot overlap. An event may start exactly when another ends (event times are kept to whole minutes). |
| Scheduling | Events start in the future, at most 2 years ahead, and last from 15 minutes to 30 days. |
| Capacity & waitlist | A full event puts new registrants on a FIFO waitlist. A seat opens when an attendee withdraws, when capacity is raised, or when an attendee's account is deactivated; the next waitlisted attendee is then promoted. Anyone the promotion would double-book is skipped and stays waitlisted. |
| Attendee schedule | An attendee cannot hold seats at two overlapping events. |
| Status lifecycle | Upcoming -> Ongoing (from the event's start date) -> Completed (once the event has started). Statuses never move backwards; Completed and Cancelled are final. |
| Cancelling an event | Its confirmed and waitlisted registrations are cancelled too; check-ins are kept as history. Completed events cannot be cancelled. |
| Check-in | Opens on the event day and stays open after the event, so attendance can be recorded late. |
| Reviews | 1 to 5 stars, for completed events only, by attendees who were checked in, within 90 days. Organizers cannot review their own events. |
| Accounts | Sign-up creates Attendee accounts; Administrators promote Organizers. Deactivating a user cancels their registrations for events that have not started. An Organizer with events that have not ended cannot be deactivated. |
| Edits | Every field is validated before anything is saved, so a rejected edit changes nothing. Capacity cannot drop below the number of confirmed attendees. |
| Reports | Attendance rate = checked-in / registered attendees at completed events. Cancelled events are left out of totals. Events that have ended but were not marked Completed are listed separately. |
| Cancelling input | Typing `cancel`, `0` or `back` at a prompt that offers it returns to the menu without changing anything. |

---

## III. Object-Oriented Programming Principles

### Encapsulation
- Every field in every model class is `private`. IDs, owners and creation times are `final` and have no setters (`User`, `Event`, `Registration`, `Feedback`; `Category` is fully immutable).
- State changes only through methods that guard their own rules: `Event.updateDetails(...)`, `cancel()`, `changeStatus(...)`; `Registration.promote()`, `checkIn(...)`, `cancel()`; `Feedback.revise(...)`; `User.changeEmail(...)`, `changePassword(...)`, `deactivate()`, `promoteToOrganizer()`.
- `User` never exposes its password: `passwordMatches(...)` checks a login attempt instead.
- A new `Registration` can only start as Confirmed or Waitlisted. Saved records are restored through private constructors in each model's `fromMap(...)`.
- Repositories keep their collections `private`; `findAll()` returns a copy, never the internal map.

### Abstraction
- `RoleMenu` (abstract) states what every role menu must provide (a title, its options, how to handle a choice) without saying how.
- `EventManagerMenu` (abstract) adds the event screens Administrators and Organizers share, leaving `canManage(Event)` abstract.
- The `EventSortStrategy` and `EventFilterStrategy` interfaces hide how sorting and filtering work from the services and menus.
- Services hide the business rules from the menus, and repositories hide storage from the services.

### Inheritance
```
RoleMenu (abstract)
├── AttendeeMenu
└── EventManagerMenu (abstract)
    ├── AdminMenu
    └── OrganizerMenu
```
- Shared screens (browsing, the events table, sort choices, creating, editing, advancing and cancelling events, and the check-in roster) are written once in a parent class and reused by each role.
- PlanO's own exceptions extend `RuntimeException`: `OperationCancelledException`, `InputClosedException` and `DataLoadException`.

### Polymorphism
- `MenuController` keeps one `RoleMenu` per `Role` and calls `display()` on the logged-in user's menu without checking which role it is.
- `RoleMenu.display()` calls `printMenuOptions()` and `handleOption(...)`, and each subclass answers with its own version.
- `canManage(Event)` allows every event in `AdminMenu` but only the organizer's own events in `OrganizerMenu`; the shared screens call it without knowing which menu they are in.
- The five sort strategies are swapped at runtime through the `EventSortStrategy` interface.

### Exception Handling
- Typing `cancel`, `0` or `back` at a cancellable prompt throws `OperationCancelledException`. Each menu catches it in one place and returns to the menu, so no workflow has to check for a cancelled input.
- `InputClosedException` ends the program cleanly when console input runs out, and `DataLoadException` stops startup when a data file is malformed.
- Model methods throw `IllegalStateException` for changes their rules do not allow; services turn these into the messages the user sees.

---

## IV. Pre-Seeded Demo Accounts

The system automatically creates this demo data on its first run:

| Role | Username | Password | Full Name | Notes |
|---|---|---|---|---|
| **ADMIN** | `admin` | `admin123` | System Administrator | Full oversight, user management, system reports |
| **ORGANIZER** | `courtney_org` | `org123` | Courtney Judge Fabello | Hosts EVT-1001, EVT-1002 (at capacity), EVT-1005, EVT-1006 (completed) |
| **ORGANIZER** | `gavin_org` | `org123` | Gavin Hadrian Lodor | Hosts EVT-1003, EVT-1004 |
| **ATTENDEE** | `axellerosh` | `user123` | Axellerosh Lubi | Registered for EVT-1001, EVT-1002; checked in at EVT-1006 |
| **ATTENDEE** | `john_doe` | `user123` | John Doe | Registered for EVT-1001, EVT-1002; checked in at EVT-1006 |
| **ATTENDEE** | `jane_smith` | `user123` | Jane Smith | Registered for EVT-1001, EVT-1002; registered for EVT-1006 but never checked in |
| **ATTENDEE** | `mark_lee` | `user123` | Mark Lee | No registrations yet |
| **ATTENDEE** | `emily_watson` | `user123` | Emily Watson | **Waitlisted** at position #1 on EVT-1002 |

> **Interactive Demo Tip:**
> 1. Log in as `axellerosh` (`user123`).
> 2. Go to **My Registrations** and cancel the registration for `EVT-1002` (Hands-on Microservices Lab), `REG-1004`.
> 3. Observe the system notification: `emily_watson` is automatically promoted from the Waitlist to Confirmed!

To reset the demo data, close PlanO and delete the `data/` folder; it is recreated on the next run.

---

## V. Project Structure

```
final-project-6-ooprog/
├── ReadMe.md                     # Project documentation
├── run.bat                       # Compile and launch PlanO (Windows)
├── run.sh                        # Compile and launch PlanO (macOS / Linux / Git Bash)
├── data/                         # JSON storage, created on the first run (not in git)
├── src/
│   └── plano/
│       ├── Main.java             # Main application entry point
│       ├── model/                # Domain models and their lifecycle rules
│       │   ├── Role.java
│       │   ├── User.java
│       │   ├── Category.java
│       │   ├── EventStatus.java
│       │   ├── Event.java
│       │   ├── RegistrationStatus.java
│       │   ├── Registration.java
│       │   └── Feedback.java
│       ├── strategy/             # Strategy Pattern implementations
│       │   ├── EventSortStrategy.java
│       │   ├── SortByDateAscStrategy.java
│       │   ├── SortByDateDescStrategy.java
│       │   ├── SortByPopularityStrategy.java
│       │   ├── SortByTitleStrategy.java
│       │   ├── SortByCapacityStrategy.java
│       │   ├── EventFilterCriteria.java
│       │   ├── EventFilterStrategy.java
│       │   └── CompositeEventFilterStrategy.java
│       ├── repository/           # Data access & persistence layer
│       │   ├── DataStore.java
│       │   ├── DataLoadException.java
│       │   ├── UserRepository.java
│       │   ├── CategoryRepository.java
│       │   ├── EventRepository.java
│       │   ├── RegistrationRepository.java
│       │   └── FeedbackRepository.java
│       ├── service/              # Business logic & validation services
│       │   ├── AuthService.java
│       │   ├── UserService.java
│       │   ├── EventService.java
│       │   ├── RegistrationService.java
│       │   ├── FeedbackService.java
│       │   └── ReportService.java
│       ├── util/                 # Terminal UI & parsing utilities
│       │   ├── ConsoleIO.java
│       │   ├── OperationCancelledException.java
│       │   ├── InputClosedException.java
│       │   ├── TableRenderer.java
│       │   ├── InputValidator.java
│       │   └── SimpleJson.java
│       └── ui/                   # Terminal user interface controllers
│           ├── MenuController.java
│           ├── RoleMenu.java         # Abstract base of every role menu
│           ├── EventManagerMenu.java # Abstract base of the Admin and Organizer menus
│           ├── AdminMenu.java
│           ├── OrganizerMenu.java
│           └── AttendeeMenu.java
└── test/
    └── plano/
        └── PlanOTest.java        # Automated test suite (116 checks)
```

---

## VI. How to Compile and Run

**Requirement:** a JDK, version 8 or newer (the course target is Java 8).

### Option 1: Using the Runner Script (Recommended)
From the project root directory:

- **Windows** (Command Prompt or PowerShell):
  ```bat
  .\run.bat
  ```
- **macOS / Linux / Git Bash:**
  ```bash
  ./run.sh
  ```

### Option 2: Using Standard Java Commands
- **Windows:**
  ```bat
  mkdir bin
  javac -encoding UTF-8 -d bin -sourcepath src src\plano\Main.java
  java -cp bin plano.Main
  ```
- **macOS / Linux:**
  ```bash
  mkdir -p bin
  javac -encoding UTF-8 -d bin -sourcepath src src/plano/Main.java
  java -cp bin plano.Main
  ```

Compiling `Main.java` with `-sourcepath src` compiles every class it uses.

### Running the Automated Test Suite
The tests use their own `test_data/` folder and delete it afterwards; your `data/` folder is not touched.

- **Windows:**
  ```bat
  mkdir bin
  javac -encoding UTF-8 -d bin -sourcepath "src;test" test\plano\PlanOTest.java
  java -cp bin plano.PlanOTest
  ```
- **macOS / Linux:**
  ```bash
  mkdir -p bin
  javac -encoding UTF-8 -d bin -sourcepath "src:test" test/plano/PlanOTest.java
  java -cp bin plano.PlanOTest
  ```

---

## VII. Development Notes

- **Keep the code Java 8 compatible.** On JDK 9 or newer, the run scripts compile with `--release 8`, which rejects anything Java 8 does not have (for example `String.isBlank()`, `Optional.isEmpty()`, `var`, or `case X ->` switches). When compiling by hand on a newer JDK, add `--release 8` yourself.
- **Keep source files ASCII-only.** Java 8 on Windows prints characters such as box-drawing lines, stars, or emoji as `?`.
- `data/`, `bin/` and `test_data/` are ignored by git.
