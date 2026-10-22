package plano.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Writes and reads the field values the model classes keep in the data files.
 * Reading is strict: a value that is missing or cannot be read throws, so
 * DataStore stops loading instead of guessing a default.
 */
final class SavedFields {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private SavedFields() {
    }

    static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_FORMAT) : "";
    }

    /**
     * Reads a saved date and time. An empty value means none was saved and returns null.
     *
     * @throws java.time.format.DateTimeParseException if the value is not a valid date and time
     */
    static LocalDateTime parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DATE_FORMAT);
    }

    /**
     * @throws IllegalArgumentException if the field is missing
     * @throws NumberFormatException if it is not a whole number
     */
    static int requireInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /** @throws IllegalArgumentException if the field is not true or false */
    static boolean requireBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("'" + key + "' must be true or false");
        }
        return (Boolean) value;
    }
}
