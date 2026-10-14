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

**PlanO** is a Java-based Event Management System built with pure Java SE (Standard Library, zero external dependencies). It provides organizations, academic clubs, and communities with a centralized platform to organize, register for, and track events.

PlanO eliminates error-prone coordination methods (spreadsheets, group chats, paper sign-up sheets) through built-in capacity management, venue scheduling conflict detection, automated FIFO waitlists with instant promotions, attendance check-ins, post-event feedback ratings, and administrative dashboards.

---

## II. Key Features & Functionalities

### 1. Role-Based Access Control (RBAC)
PlanO separates administrative governance from day-to-day event operations across three distinct roles:
- **Administrator (`ADMIN`):**
  - Full system oversight: view, edit, or cancel any event across the entire system.
  - User management: view all user accounts, deactivate/reactivate users, and promote attendees to organizers.
  - Category management: add, edit, or remove event categories.
  - System-wide analytical reports: view overall attendance rates, category popularity, and organizer leaderboards.
- **Event Organizer (`ORGANIZER`):**
  - Create, update, and cancel their **own** events.
  - Manage attendee rosters and mark attendees as "Checked In" on event day.
  - View scoped analytics, fill rates, attendance percentages, and post-event feedback ratings for their own events.
- **Regular User / Attendee (`ATTENDEE`):**
  - Discover and search upcoming public events with live filter and sorting strategies.
  - RSVP and register for events with automatic capacity validation and waitlist placement.
  - Manage personal registrations and withdraw/cancel reservations (which triggers auto-promotion for waitlisted users).
  - Submit 1-to-5 star reviews and feedback comments for completed events attended.
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
  - Users and admins can swap sorting strategies dynamically from the console UI.
- **Repository Pattern (`plano.repository`):**
  - Decouples in-memory domain collections from persistence storage.
  - `UserRepository`, `EventRepository`, `RegistrationRepository`, `FeedbackRepository`, `CategoryRepository`.
- **Pure Java JSON Persistence (`DataStore` & `SimpleJson`):**
  - Implements a pure standard Java recursive-descent JSON parser and serializer.
  - All data is saved automatically to human-readable JSON files in the `data/` directory (`users.json`, `events.json`, `registrations.json`, `feedbacks.json`, `categories.json`).
  - Automatically seeds realistic test data on the first run.
- **Scheduling Conflict Detection (`EventService`):**
  - Detects overlapping time windows at the same venue for non-cancelled events (`start1 < end2 && end1 > start2`).
  - Flags conflicts with exact conflicting event IDs, titles, and time intervals to prevent double-booking.
- **Capacity & FIFO Waitlist Auto-Promotion (`RegistrationService`):**
  - When an event hits maximum capacity, registrants are automatically placed on a FIFO waitlist with an assigned queue number.
  - When a confirmed registrant cancels or withdraws, the system immediately promotes the first attendee in the waitlist queue to `CONFIRMED`.
- **Attendance Check-In & Post-Event Reviews:**
  - Organizers and Admins can check in confirmed attendees on the event day.
  - Attendees can only submit reviews for events that have concluded (`COMPLETED`) and that they were registered for.

---

## III. Pre-Seeded Demo Accounts

The system automatically initializes seed data on startup for testing:

| Role | Username | Password | Full Name | Notes |
|---|---|---|---|---|
| **ADMIN** | `admin` | `admin123` | System Administrator | Full oversight, user management, system reports |
| **ORGANIZER** | `courtney_org` | `org123` | Courtney Judge Fabello | Hosts EVT-1001, EVT-1002 (at capacity), EVT-1006 (completed) |
| **ORGANIZER** | `gavin_org` | `org123` | Gavin Hadrian Lodor | Hosts EVT-1003, EVT-1004 |
| **ATTENDEE** | `axellerosh` | `user123` | Axellerosh Lubi | Registered for EVT-1001, EVT-1002, EVT-1006 |
| **ATTENDEE** | `john_doe` | `user123` | John Doe | Registered for EVT-1001, EVT-1002 |
| **ATTENDEE** | `emily_watson` | `user123` | Emily Watson | **Waitlisted** at position #1 on EVT-1002 |

> **Interactive Demo Tip:**
> 1. Log in as `axellerosh` (`user123`).
> 2. Go to **My Registrations** and cancel the registration for `EVT-1002` (Hands-on Microservices Lab).
> 3. Observe the system notification: `emily_watson` is automatically promoted from the Waitlist to Confirmed!

---

## IV. Project Structure

```
finals/
├── ReadMe.md                     # Project documentation
├── run.sh                        # Bash script to compile and launch PlanO
├── data/                         # Persistent JSON storage files
│   ├── users.json
│   ├── categories.json
│   ├── events.json
│   ├── registrations.json
│   └── feedbacks.json
├── src/
│   └── plano/
│       ├── Main.java             # Main application entry point
│       ├── model/                # Domain models (POJOs)
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
│       │   ├── TableRenderer.java
│       │   ├── InputValidator.java
│       │   └── SimpleJson.java
│       └── ui/                   # Terminal user interface controllers
│           ├── MenuController.java
│           ├── AdminMenu.java
│           ├── OrganizerMenu.java
│           └── AttendeeMenu.java
└── test/
    └── plano/
        └── PlanOTest.java        # Comprehensive automated test suite (36 tests)
```

---

## V. How to Compile and Run

### Option 1: Using the Runner Script (Recommended)
From the project root directory, run:
```bash
./run.sh
```

### Option 2: Using Standard Java Commands
```bash
# 1. Compile source files into bin/
mkdir -p bin
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
rm -f sources.txt

# 2. Run the application
java -cp bin plano.Main
```

### Running the Automated Test Suite
To execute the automated regression test suite covering all 36 test cases:
```bash
javac -d bin -sourcepath "src:test" test/plano/PlanOTest.java
java -cp bin plano.PlanOTest
```
