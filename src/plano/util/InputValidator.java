package plano.util;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Common input validation routines for strings, emails, dates, and scheduling intervals.
 */
public class InputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
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

    public static boolean isValidTimeInterval(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && end.isAfter(start);
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
