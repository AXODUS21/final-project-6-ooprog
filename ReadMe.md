Initial project proposal:

Group #: Group - 6
 Group Members: Axellerosh F. Lubi, Courtney Judge P. Fabello, Gavin Hadrian M. Lodor
 Section: C2B
I. Introduction
a. Project Overview:
PlanO is a Java-based Event Management System that allows organizers to create and manage free events (conferences, seminars, workshops, meetups, community gatherings) while allowing users to discover, register for, and track events they're attending. It replaces manual, error-prone methods of event coordination — spreadsheets, group chats, paper sign-up sheets — with a centralized system that handles registration, capacity limits, scheduling, and post-event feedback in one place.
b. Objective:
PlanO aims to make organizing and attending events more structured, accessible, and reliable for schools, organizations, and communities that don't have access to enterprise-level event software. Specifically, the system aims to:
Reduce coordination overhead for event organizers by centralizing event creation, registration tracking, and attendee management in a single system.
Prevent common planning failures — overbooking, double-registration, scheduling conflicts — through built-in validation and capacity enforcement.
Give attendees a reliable, organized way to discover events, register, and keep track of what they've signed up for, replacing scattered sign-up methods.
Create accountability and continuous improvement through a post-event feedback and rating system, helping organizers improve future events.
Distribute event management responsibly by separating system-level administration from day-to-day event organizing, so organizations can let multiple people run events without giving everyone full administrative control.
Make structured event management accessible to smaller organizations (student orgs, community groups, small businesses) that can't afford commercial event platforms.
II. System Features and Functionalities
a. Core Functionalities:
Create: Add new events (name, description, date/time, venue, category, capacity); register for an event (attendee); create user accounts (all roles).
Read: Browse/search all events; filter/sort events by category, date, or popularity; view event details and remaining capacity; view a user's own registration history; view attendee list for an event (Organizer/Admin).
Update: Edit event details (Organizer — own events only; Admin — any event); update user profile info; change/cancel a registration.
Delete: Cancel/remove an event (Organizer — own events only; Admin — any event); remove/withdraw a registration; deactivate a user account (Admin).
Additional operations:
Capacity & waitlist management — once an event hits capacity, new registrants are placed on a waitlist and auto-promoted if a spot opens.
Event search & filtering (by category, date range, location) — implemented via the Strategy pattern, so sorting/filtering logic (by date, popularity, alphabetical) can be swapped at runtime.
Scheduling conflict detection — flags if an Organizer/Admin tries to book the same venue for overlapping times.
Check-in / attendance tracking — Organizer/Admin marks attendees as "checked in" on the event day.
Post-event feedback & ratings — attendees rate/review an event after it's marked completed.
Admin reports/dashboard — total events, registrations per event, most popular categories, attendance rate (checked-in vs. registered), organizer activity.
Input validation everywhere — required fields, future-only event dates, positive capacity values, valid email formats, duplicate registration prevention.
III. Roles
a. User Types and Permissions:
Admin:
Full system oversight — can view, edit, or delete any event, regardless of which Organizer created it.
Manage user accounts across all roles (view, deactivate, promote a user to Organizer).
View system-wide reports/dashboard analytics.
Manage event categories.
Organizer:
Create, edit, and delete their own events only.
View and manage attendee lists and check-ins for their own events.
View reports/analytics limited to their own events.
Cannot manage other organizers' events or manage user accounts.
Regular User (Attendee):
Browse and search available events.
Register/RSVP for events (subject to capacity/waitlist).
View and manage their own registrations (cancel, view status).
Submit feedback/rating after attending an event.
Cannot create, edit, or delete events, and cannot view other users' registrations.
