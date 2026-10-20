package plano.util;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Common input validation routines for strings, emails, usernames, dates,
 * numerical bounds, and scheduling intervals.
 */
public class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_.-]{3,30}$"
    );

    private static final Pattern FULL_NAME_PATTERN = Pattern.compile(
            "^[a-zA-Z\\s.'-]{2,50}$"
    );

    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^EVT-\\d+$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^USR-\\d+$");
    private static final Pattern REGISTRATION_ID_PATTERN = Pattern.compile("^REG-\\d+$");
    private static final Pattern CATEGORY_ID_PATTERN = Pattern.compile("^CAT-\\d+$");

    private static final Pattern INTEGER_PATTERN = Pattern.compile("^0$|^[1-9]\\d*$");
    private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("^[1-9]\\d*$");

    public static final int MAX_EVENT_HORIZON_YEARS = 2;

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        String trimmed = password.trim();
        if (trimmed.length() < 6 || password.length() > 64) return false;
        boolean hasLetter = trimmed.matches(".*[a-zA-Z].*");
        boolean hasDigit = trimmed.matches(".*\\d.*");
        return hasLetter && hasDigit;
    }

    public static boolean isValidFullName(String name) {
        if (name == null) return false;
        return FULL_NAME_PATTERN.matcher(name.trim()).matches();
    }

    public static boolean isValidEventTitle(String title) {
        if (title == null) return false;
        int len = title.trim().length();
        return len >= 3 && len <= 100;
    }

    public static boolean isValidVenue(String venue) {
        if (venue == null) return false;
        int len = venue.trim().length();
        return len >= 2 && len <= 100;
    }

    public static boolean isValidCategoryName(String name) {
        if (name == null) return false;
        int len = name.trim().length();
        return len >= 2 && len <= 50;
    }

    public static boolean isValidComment(String comment) {
        if (comment == null) return false;
        String trimmed = comment.trim();
        int len = trimmed.length();
        if (len < 3 || len > 1000) return false;
        // Check for spam: repetitive single character (e.g. "aaaaa", ".....", "!!!!!")
        if (trimmed.matches("^(.)\\1{4,}$")) return false;
        // Must contain at least one letter or digit
        return trimmed.matches(".*[a-zA-Z0-9].*");
    }

    public static boolean isValidCapacity(int capacity) {
        return capacity >= 1 && capacity <= 50000;
    }

    public static boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }

    public static boolean isValidEventId(String id) {
        return id != null && EVENT_ID_PATTERN.matcher(id.trim()).matches();
    }

    public static boolean isValidUserId(String id) {
        return id != null && USER_ID_PATTERN.matcher(id.trim()).matches();
    }

    public static boolean isValidRegistrationId(String id) {
        return id != null && REGISTRATION_ID_PATTERN.matcher(id.trim()).matches();
    }

    public static boolean isValidCategoryId(String id) {
        return id != null && CATEGORY_ID_PATTERN.matcher(id.trim()).matches();
    }

    /**
     * Checks if the input has leading zeroes (e.g. '01', '007') which are disallowed.
     */
    public static boolean hasLeadingZeroes(String str) {
        if (str == null) return false;
        return str.trim().matches("^0\\d+$");
    }

    /**
     * Checks if the input contains a decimal point or trailing decimal zeroes (e.g. '1.0', '1.00').
     */
    public static boolean hasDecimalOrTrailingZeroes(String str) {
        if (str == null) return false;
        return str.contains(".");
    }

    public static boolean isValidIntegerFormat(String str) {
        if (str == null) return false;
        return INTEGER_PATTERN.matcher(str.trim()).matches();
    }

    public static boolean isValidPositiveIntegerFormat(String str) {
        if (str == null) return false;
        return POSITIVE_INTEGER_PATTERN.matcher(str.trim()).matches();
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static boolean isPositive(int number) {
        return number > 0;
    }

    public static boolean isFuture(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isAfter(LocalDateTime.now());
    }

    /**
     * Checks if a scheduled date/time is within the allowable booking horizon (max 2 years).
     */
    public static boolean isWithinMaxHorizon(LocalDateTime dateTime) {
        if (dateTime == null) return false;
        return dateTime.isBefore(LocalDateTime.now().plusYears(MAX_EVENT_HORIZON_YEARS));
    }

    public static boolean isValidTimeInterval(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && end.isAfter(start);
    }

    /**
     * Enforces that an event lasts at least 15 minutes and at most 30 continuous days.
     */
    public static boolean isValidEventDuration(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return false;
        }
        long minutes = java.time.Duration.between(start, end).toMinutes();
        return minutes >= 15 && minutes <= (30L * 24 * 60);
    }

    /**
     * Sanitizes a string by replacing newline, tab, and control characters with spaces
     * to preserve ASCII table layout and console rendering.
     */
    public static String sanitizeSingleLine(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    /**
     * Checks whether two time windows overlap.
     * Overlap occurs if start1 < end2 AND end1 > start2.
     */
    public static boolean doIntervalsOverlap(LocalDateTime start1, LocalDateTime end1,
                                            LocalDateTime start2, LocalDateTime end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    public static boolean isVenueMatch(String venue1, String venue2) {
        if (venue1 == null || venue2 == null) return false;
        return venue1.trim().equalsIgnoreCase(venue2.trim());
    }
}

