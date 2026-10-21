package plano.repository;

import plano.model.*;
import plano.util.SimpleJson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages persistent JSON storage in the data directory.
 * Loads every file on startup (all or nothing) and seeds demo data when no data exists yet.
 */
public class DataStore {

    private final String dataDir;
    private final UserRepository userRepository = new UserRepository();
    private final CategoryRepository categoryRepository = new CategoryRepository();
    private final EventRepository eventRepository = new EventRepository();
    private final RegistrationRepository registrationRepository = new RegistrationRepository();
    private final FeedbackRepository feedbackRepository = new FeedbackRepository();

    public DataStore() {
        this("data");
    }

    /**
     * @throws DataLoadException if an existing data file cannot be read
     */
    public DataStore(String dataDir) {
        this.dataDir = dataDir;
        initAndLoad();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public CategoryRepository getCategoryRepository() {
        return categoryRepository;
    }

    public EventRepository getEventRepository() {
        return eventRepository;
    }

    public RegistrationRepository getRegistrationRepository() {
        return registrationRepository;
    }

    public FeedbackRepository getFeedbackRepository() {
        return feedbackRepository;
    }

    // ==================== ID GENERATORS ====================
    // Each new ID is one above the highest existing number, so an ID is never reused after a deletion.

    public synchronized String nextUserId() {
        return nextId("USR-", 1000, userRepository.findAll().stream().map(User::getUserId).collect(Collectors.toList()));
    }

    public synchronized String nextCategoryId() {
        return nextId("CAT-", 100, categoryRepository.findAll().stream().map(Category::getCategoryId).collect(Collectors.toList()));
    }

    public synchronized String nextEventId() {
        return nextId("EVT-", 1000, eventRepository.findAll().stream().map(Event::getEventId).collect(Collectors.toList()));
    }

    public synchronized String nextRegistrationId() {
        return nextId("REG-", 1000, registrationRepository.findAll().stream().map(Registration::getRegistrationId).collect(Collectors.toList()));
    }

    public synchronized String nextFeedbackId() {
        return nextId("FB-", 1000, feedbackRepository.findAll().stream().map(Feedback::getFeedbackId).collect(Collectors.toList()));
    }

    private static String nextId(String prefix, int base, List<String> existingIds) {
        int highest = base;
        for (String id : existingIds) {
            if (id == null || !id.startsWith(prefix)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(id.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // An ID that does not follow the PREFIX-number format does not affect numbering.
            }
        }
        return prefix + (highest + 1);
    }

    // ==================== INITIALIZATION & PERSISTENCE ====================

    private void initAndLoad() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File usersFile = new File(dir, "users.json");
        if (!usersFile.exists()) {
            seedDefaultData();
            saveAll();
        } else {
            loadAll();
        }
    }

    public synchronized void saveAll() {
        saveUsers();
        saveCategories();
        saveEvents();
        saveRegistrations();
        saveFeedbacks();
    }

    public synchronized void saveUsers() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            list.add(u.toMap());
        }
        writeFile("users.json", SimpleJson.serialize(list));
    }

    public synchronized void saveCategories() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Category c : categoryRepository.findAll()) {
            list.add(c.toMap());
        }
        writeFile("categories.json", SimpleJson.serialize(list));
    }

    public synchronized void saveEvents() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Event e : eventRepository.findAll()) {
            list.add(e.toMap());
        }
        writeFile("events.json", SimpleJson.serialize(list));
    }

    public synchronized void saveRegistrations() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Registration r : registrationRepository.findAll()) {
            list.add(r.toMap());
        }
        writeFile("registrations.json", SimpleJson.serialize(list));
    }

    public synchronized void saveFeedbacks() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Feedback fb : feedbackRepository.findAll()) {
            list.add(fb.toMap());
        }
        writeFile("feedbacks.json", SimpleJson.serialize(list));
    }

    /**
     * Reads all five files before touching any repository. If any file or record cannot be read,
     * nothing is loaded and a DataLoadException names the problem, leaving the files untouched.
     */
    private void loadAll() {
        List<User> users = readRecords("users.json", User::fromMap, User::getUserId);
        List<Category> categories = readRecords("categories.json", Category::fromMap, Category::getCategoryId);
        List<Event> events = readRecords("events.json", Event::fromMap, Event::getEventId);
        List<Registration> registrations = readRecords("registrations.json", Registration::fromMap, Registration::getRegistrationId);
        List<Feedback> feedbacks = readRecords("feedbacks.json", Feedback::fromMap, Feedback::getFeedbackId);

        userRepository.clear();
        for (User u : users) userRepository.add(u);
        categoryRepository.clear();
        for (Category c : categories) categoryRepository.add(c);
        eventRepository.clear();
        for (Event e : events) eventRepository.add(e);
        registrationRepository.clear();
        for (Registration r : registrations) registrationRepository.add(r);
        feedbackRepository.clear();
        for (Feedback fb : feedbacks) feedbackRepository.add(fb);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> readRecords(String filename, Function<Map<String, Object>, T> mapper, Function<T, String> idOf) {
        List<T> records = new ArrayList<>();
        String json = readFile(filename);
        if (json == null) {
            return records; // A missing file just means there are no records of that kind yet.
        }

        String where = Paths.get(dataDir, filename).toString();
        Object parsed;
        try {
            parsed = SimpleJson.parse(json);
        } catch (RuntimeException e) {
            throw new DataLoadException(where, "the file is not valid JSON (" + e.getMessage() + ")");
        }
        if (!(parsed instanceof List)) {
            throw new DataLoadException(where, "expected a JSON array of records");
        }

        int index = 0;
        for (Object item : (List<Object>) parsed) {
            index++;
            if (!(item instanceof Map)) {
                throw new DataLoadException(where, "record #" + index + " is not a JSON object");
            }
            T record;
            try {
                record = mapper.apply((Map<String, Object>) item);
            } catch (RuntimeException e) {
                throw new DataLoadException(where, "record #" + index + " could not be read (" + e.getMessage() + ")");
            }
            String id = idOf.apply(record);
            if (id == null || id.trim().isEmpty()) {
                throw new DataLoadException(where, "record #" + index + " has no ID");
            }
            records.add(record);
        }
        return records;
    }

    private void writeFile(String filename, String content) {
        try {
            Path path = Paths.get(dataDir, filename);
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to write to file " + filename + ": " + e.getMessage());
        }
    }

    private String readFile(String filename) {
        Path path = Paths.get(dataDir, filename);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataLoadException(path.toString(), "the file could not be read (" + e.getMessage() + ")");
        }
    }

    // ==================== SEED DATA ====================

    private void seedDefaultData() {
        // 1. Users
        User admin = new User("USR-1001", "admin", "admin123", "System Administrator", "admin@plano.edu", Role.ADMIN);
        User organizer1 = new User("USR-1002", "courtney_org", "org123", "Courtney Judge Fabello", "courtney@plano.edu", Role.ORGANIZER);
        User organizer2 = new User("USR-1003", "gavin_org", "org123", "Gavin Hadrian Lodor", "gavin@plano.edu", Role.ORGANIZER);
        User user1 = new User("USR-1004", "axellerosh", "user123", "Axellerosh Lubi", "axellerosh@plano.edu", Role.ATTENDEE);
        User user2 = new User("USR-1005", "john_doe", "user123", "John Doe", "john.doe@example.com", Role.ATTENDEE);
        User user3 = new User("USR-1006", "jane_smith", "user123", "Jane Smith", "jane.smith@example.com", Role.ATTENDEE);
        User user4 = new User("USR-1007", "mark_lee", "user123", "Mark Lee", "mark.lee@example.com", Role.ATTENDEE);
        User user5 = new User("USR-1008", "emily_watson", "user123", "Emily Watson", "emily.w@example.com", Role.ATTENDEE);

        userRepository.add(admin);
        userRepository.add(organizer1);
        userRepository.add(organizer2);
        userRepository.add(user1);
        userRepository.add(user2);
        userRepository.add(user3);
        userRepository.add(user4);
        userRepository.add(user5);

        // 2. Categories
        Category catConf = new Category("CAT-101", "Conference", "Large formal presentations, keynotes, and academic lectures");
        Category catWork = new Category("CAT-102", "Workshop", "Interactive, hands-on technical or creative training sessions");
        Category catSemi = new Category("CAT-103", "Seminar", "Guided instructional seminars on specific industry/academic topics");
        Category catMeet = new Category("CAT-104", "Meetup", "Casual gatherings and networking for communities of practice");
        Category catComm = new Category("CAT-105", "Community Gathering", "Events fostering community connection, volunteer drives, and outreach");
        Category catHack = new Category("CAT-106", "Hackathon", "Intensive collaborative coding and problem-solving competitions");

        categoryRepository.add(catConf);
        categoryRepository.add(catWork);
        categoryRepository.add(catSemi);
        categoryRepository.add(catMeet);
        categoryRepository.add(catComm);
        categoryRepository.add(catHack);

        // 3. Events (dates are relative to the first run, so the demo events are always upcoming)
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        // EVT-1001: AI Summit (Upcoming Conference)
        Event e1 = new Event("EVT-1001", "AI & Modern Java Summit 2026",
                "Deep dive into modern Java 25 features, agentic workflows, and cloud architecture.",
                "USR-1002", "Conference", "Grand Auditorium A",
                now.plusDays(7).withHour(9).withMinute(0),
                now.plusDays(7).withHour(17).withMinute(0),
                30);

        // EVT-1002: Microservices Workshop (Capacity 3 -> demonstrates the waitlist)
        Event e2 = new Event("EVT-1002", "Hands-on Microservices Lab",
                "Step-by-step building of resilient distributed systems with practical exercises.",
                "USR-1002", "Workshop", "Computer Lab 304",
                now.plusDays(10).withHour(13).withMinute(30),
                now.plusDays(10).withHour(16).withMinute(30),
                3);

        // EVT-1003: UI/UX Seminar
        Event e3 = new Event("EVT-1003", "Design Systems & User Experience",
                "Creating scalable design tokens and accessible web interfaces.",
                "USR-1003", "Seminar", "Audio-Visual Room 2",
                now.plusDays(14).withHour(10).withMinute(0),
                now.plusDays(14).withHour(12).withMinute(0),
                25);

        // EVT-1004: Open Source Meetup
        Event e4 = new Event("EVT-1004", "Open Source Contributors Meetup",
                "Networking session for students and professionals contributing to open source.",
                "USR-1003", "Meetup", "Student Center Lounge",
                now.plusDays(20).withHour(15).withMinute(0),
                now.plusDays(20).withHour(18).withMinute(0),
                15);

        // EVT-1005: Community Clean Drive
        Event e5 = new Event("EVT-1005", "Campus Green Community Drive",
                "Tree planting and recycling awareness campaign across campus.",
                "USR-1002", "Community Gathering", "University Quadrangle",
                now.plusDays(25).withHour(8).withMinute(0),
                now.plusDays(25).withHour(11).withMinute(30),
                50);

        // EVT-1006: Completed event to showcase check-ins and ratings
        Event e6 = new Event("EVT-1006", "CodeCraft 2026 Orientation",
                "Welcoming session and kickoff for competitive programming participants.",
                "USR-1002", "Seminar", "Multimedia Amphitheater",
                now.minusDays(5).withHour(14).withMinute(0),
                now.minusDays(5).withHour(16).withMinute(0),
                40);
        e6.setStatus(EventStatus.COMPLETED);

        eventRepository.add(e1);
        eventRepository.add(e2);
        eventRepository.add(e3);
        eventRepository.add(e4);
        eventRepository.add(e5);
        eventRepository.add(e6);

        // 4. Registrations
        // EVT-1001: 3 attendees
        registrationRepository.add(new Registration("REG-1001", "EVT-1001", "USR-1004", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1002", "EVT-1001", "USR-1005", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1003", "EVT-1001", "USR-1006", RegistrationStatus.CONFIRMED));

        // EVT-1002: capacity 3, fully booked
        registrationRepository.add(new Registration("REG-1004", "EVT-1002", "USR-1004", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1005", "EVT-1002", "USR-1005", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1006", "EVT-1002", "USR-1006", RegistrationStatus.CONFIRMED));
        // emily_watson waits at position #1 (the README demo promotes her)
        registrationRepository.add(new Registration("REG-1007", "EVT-1002", "USR-1008", RegistrationStatus.WAITLISTED));

        // EVT-1006 (Completed): two checked-in attendees and one no-show
        Registration regComp1 = new Registration("REG-1008", "EVT-1006", "USR-1004", RegistrationStatus.CHECKED_IN);
        regComp1.setCheckedInAt(now.minusDays(5).withHour(14).withMinute(5));
        Registration regComp2 = new Registration("REG-1009", "EVT-1006", "USR-1005", RegistrationStatus.CHECKED_IN);
        regComp2.setCheckedInAt(now.minusDays(5).withHour(14).withMinute(10));
        Registration regComp3 = new Registration("REG-1010", "EVT-1006", "USR-1006", RegistrationStatus.CONFIRMED); // registered but absent

        registrationRepository.add(regComp1);
        registrationRepository.add(regComp2);
        registrationRepository.add(regComp3);

        // 5. Feedback for EVT-1006 (from the two checked-in attendees)
        Feedback fb1 = new Feedback("FB-1001", "EVT-1006", "USR-1004", 5, "Incredible kickoff session! Very informative and well-structured.");
        Feedback fb2 = new Feedback("FB-1002", "EVT-1006", "USR-1005", 4, "Great speakers and insightful presentation. Excited for the upcoming hackathon.");
        feedbackRepository.add(fb1);
        feedbackRepository.add(fb2);
    }
}
