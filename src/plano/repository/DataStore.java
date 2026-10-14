package plano.repository;

import plano.model.*;
import plano.util.SimpleJson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages persistent JSON storage in the 'data/' directory.
 * Automatically loads data on startup and provides auto-seeding if empty.
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

    public synchronized String nextUserId() {
        return "USR-" + (1000 + userRepository.size() + 1);
    }

    public synchronized String nextCategoryId() {
        return "CAT-" + (100 + categoryRepository.size() + 1);
    }

    public synchronized String nextEventId() {
        return "EVT-" + (1000 + eventRepository.size() + 1);
    }

    public synchronized String nextRegistrationId() {
        return "REG-" + (1000 + registrationRepository.size() + 1);
    }

    public synchronized String nextFeedbackId() {
        return "FB-" + (1000 + feedbackRepository.size() + 1);
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

    @SuppressWarnings("unchecked")
    private void loadAll() {
        try {
            // Load Users
            String usersJson = readFile("users.json");
            if (usersJson != null) {
                Object parsed = SimpleJson.parse(usersJson);
                if (parsed instanceof List) {
                    userRepository.clear();
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            userRepository.add(User.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }

            // Load Categories
            String catJson = readFile("categories.json");
            if (catJson != null) {
                Object parsed = SimpleJson.parse(catJson);
                if (parsed instanceof List) {
                    categoryRepository.clear();
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            categoryRepository.add(Category.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }

            // Load Events
            String evtJson = readFile("events.json");
            if (evtJson != null) {
                Object parsed = SimpleJson.parse(evtJson);
                if (parsed instanceof List) {
                    eventRepository.clear();
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            eventRepository.add(Event.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }

            // Load Registrations
            String regJson = readFile("registrations.json");
            if (regJson != null) {
                Object parsed = SimpleJson.parse(regJson);
                if (parsed instanceof List) {
                    registrationRepository.clear();
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            registrationRepository.add(Registration.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }

            // Load Feedbacks
            String fbJson = readFile("feedbacks.json");
            if (fbJson != null) {
                Object parsed = SimpleJson.parse(fbJson);
                if (parsed instanceof List) {
                    feedbackRepository.clear();
                    for (Object item : (List<?>) parsed) {
                        if (item instanceof Map) {
                            feedbackRepository.add(Feedback.fromMap((Map<String, Object>) item));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Error loading persistence files: " + e.getMessage());
        }
    }

    private void writeFile(String filename, String content) {
        try {
            Path path = Paths.get(dataDir, filename);
            Files.writeString(path, content);
        } catch (IOException e) {
            System.err.println("Failed to write to file " + filename + ": " + e.getMessage());
        }
    }

    private String readFile(String filename) {
        try {
            Path path = Paths.get(dataDir, filename);
            if (!Files.exists(path)) return null;
            return Files.readString(path);
        } catch (IOException e) {
            return null;
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

        // 3. Events
        LocalDateTime now = LocalDateTime.now();

        // EVT-1001: AI Summit (Upcoming Conference)
        Event e1 = new Event("EVT-1001", "AI & Modern Java Summit 2026",
                "Deep dive into modern Java 25 features, agentic workflows, and cloud architecture.",
                "USR-1002", "Conference", "Grand Auditorium A",
                now.plusDays(7).withHour(9).withMinute(0).withSecond(0),
                now.plusDays(7).withHour(17).withMinute(0).withSecond(0),
                30);

        // EVT-1002: Microservices Workshop (Capacity 3 -> Will demonstrate Waitlist!)
        Event e2 = new Event("EVT-1002", "Hands-on Microservices Lab",
                "Step-by-step building of resilient distributed systems with practical exercises.",
                "USR-1002", "Workshop", "Computer Lab 304",
                now.plusDays(10).withHour(13).withMinute(30).withSecond(0),
                now.plusDays(10).withHour(16).withMinute(30).withSecond(0),
                3);

        // EVT-1003: UI/UX Seminar
        Event e3 = new Event("EVT-1003", "Design Systems & User Experience",
                "Creating scalable design tokens and accessible web interfaces.",
                "USR-1003", "Seminar", "Audio-Visual Room 2",
                now.plusDays(14).withHour(10).withMinute(0).withSecond(0),
                now.plusDays(14).withHour(12).withMinute(0).withSecond(0),
                25);

        // EVT-1004: Open Source Meetup
        Event e4 = new Event("EVT-1004", "Open Source Contributors Meetup",
                "Networking session for students and professionals contributing to open source.",
                "USR-1003", "Meetup", "Student Center Lounge",
                now.plusDays(20).withHour(15).withMinute(0).withSecond(0),
                now.plusDays(20).withHour(18).withMinute(0).withSecond(0),
                15);

        // EVT-1005: Community Clean Drive
        Event e5 = new Event("EVT-1005", "Campus Green Community Drive",
                "Tree planting and recycling awareness campaign across campus.",
                "USR-1002", "Community Gathering", "University Quadrangle",
                now.plusDays(25).withHour(8).withMinute(0).withSecond(0),
                now.plusDays(25).withHour(11).withMinute(30).withSecond(0),
                50);

        // EVT-1006: Completed Event to showcase check-ins and ratings
        Event e6 = new Event("EVT-1006", "CodeCraft 2026 Orientation",
                "Welcoming session and kickoff for competitive programming participants.",
                "USR-1002", "Seminar", "Multimedia Amphitheater",
                now.minusDays(5).withHour(14).withMinute(0).withSecond(0),
                now.minusDays(5).withHour(16).withMinute(0).withSecond(0),
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

        // EVT-1002: Capacity 3!
        // 3 Confirmed:
        registrationRepository.add(new Registration("REG-1004", "EVT-1002", "USR-1004", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1005", "EVT-1002", "USR-1005", RegistrationStatus.CONFIRMED));
        registrationRepository.add(new Registration("REG-1006", "EVT-1002", "USR-1006", RegistrationStatus.CONFIRMED));
        // 1 Waitlisted:
        Registration waitlisted = new Registration("REG-1007", "EVT-1002", "USR-1007", RegistrationStatus.WAITLISTED);
        registrationRepository.add(waitlisted);

        // EVT-1006 (Completed): Checked-in attendees
        Registration regComp1 = new Registration("REG-1008", "EVT-1006", "USR-1004", RegistrationStatus.CHECKED_IN);
        regComp1.setCheckedInAt(now.minusDays(5).withHour(14).withMinute(5));
        Registration regComp2 = new Registration("REG-1009", "EVT-1006", "USR-1005", RegistrationStatus.CHECKED_IN);
        regComp2.setCheckedInAt(now.minusDays(5).withHour(14).withMinute(10));
        Registration regComp3 = new Registration("REG-1010", "EVT-1006", "USR-1006", RegistrationStatus.CONFIRMED); // registered but absent

        registrationRepository.add(regComp1);
        registrationRepository.add(regComp2);
        registrationRepository.add(regComp3);

        // 5. Feedbacks for EVT-1006
        Feedback fb1 = new Feedback("FB-1001", "EVT-1006", "USR-1004", 5, "Incredible kickoff session! Very informative and well-structured.");
        Feedback fb2 = new Feedback("FB-1002", "EVT-1006", "USR-1005", 4, "Great speakers and insightful presentation. Excited for the upcoming hackathon.");
        feedbackRepository.add(fb1);
        feedbackRepository.add(fb2);
    }
}
