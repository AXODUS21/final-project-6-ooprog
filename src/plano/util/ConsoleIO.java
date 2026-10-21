package plano.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Handles terminal I/O, ANSI colors, formatted banners, and safe user input prompts.
 * All output is plain ASCII so it displays the same on every console and Java version.
 *
 * Prompts that accept 'cancel', '0' or 'back' throw {@link OperationCancelledException};
 * the menus catch it and return the user to the menu.
 */
public class ConsoleIO {
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");

    private static final Scanner scanner = new Scanner(System.in);

    /** Repeats a string, e.g. repeat("=", 3) returns "===". */
    public static String repeat(String text, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(text);
        }
        return sb.toString();
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    public static void printBanner(String title, String subtitle) {
        int width = 76;
        String line = repeat("=", width);
        System.out.println(CYAN + BOLD + "+" + line + "+" + RESET);
        System.out.println(CYAN + BOLD + "|" + centerText(title, width) + "|" + RESET);
        if (!isBlank(subtitle)) {
            System.out.println(CYAN + "|" + centerText(subtitle, width) + "|" + RESET);
        }
        System.out.println(CYAN + BOLD + "+" + line + "+" + RESET);
    }

    public static void printSection(String title) {
        System.out.println();
        System.out.println(BOLD + BLUE + "> " + title.toUpperCase() + RESET);
        System.out.println(DIM + repeat("-", Math.max(40, title.length() + 4)) + RESET);
    }

    public static void printSuccess(String message) {
        System.out.println(GREEN + BOLD + "[OK] " + RESET + GREEN + message + RESET);
    }

    public static void printError(String message) {
        System.out.println(RED + BOLD + "[ERROR] " + RESET + RED + message + RESET);
    }

    public static void printWarning(String message) {
        System.out.println(YELLOW + BOLD + "[WARNING] " + RESET + YELLOW + message + RESET);
    }

    public static void printInfo(String message) {
        System.out.println(CYAN + "[INFO] " + message + RESET);
    }

    public static boolean isCancelToken(String input) {
        if (input == null) return false;
        String s = input.trim();
        return s.equalsIgnoreCase("cancel") || s.equals("0") || s.equalsIgnoreCase(":back") || s.equalsIgnoreCase("back");
    }

    /**
     * Returns the input unchanged, or throws if it is a cancel token.
     *
     * @throws OperationCancelledException if the user asked to cancel
     */
    private static String unlessCancelled(String input) {
        if (isCancelToken(input)) {
            throw new OperationCancelledException();
        }
        return input;
    }

    /**
     * Reads one trimmed line.
     *
     * @throws InputClosedException if the input stream has ended
     */
    public static String readString(String prompt) {
        System.out.print(BOLD + prompt + ": " + RESET);
        if (!scanner.hasNextLine()) {
            throw new InputClosedException();
        }
        return scanner.nextLine().trim();
    }

    public static String readNonEmptyString(String prompt) {
        while (true) {
            String val = readString(prompt);
            if (!isBlank(val)) {
                return val;
            }
            printError("This field cannot be empty. Please enter a valid value.");
        }
    }

    /**
     * Reads a non-empty line.
     *
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readCancellableString(String prompt) {
        return unlessCancelled(readNonEmptyString(prompt));
    }

    public static String readOptionalString(String prompt, String defaultValue) {
        String promptWithDefault = isBlank(defaultValue)
                ? prompt
                : prompt + " [" + defaultValue + "]";
        String val = readString(promptWithDefault);
        if (isBlank(val) && defaultValue != null) {
            return defaultValue;
        }
        return val;
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            String input = readString(prompt + " (" + min + " - " + max + ")");
            if (isBlank(input)) {
                printError("This field cannot be empty. Please enter a number.");
                continue;
            }
            if (InputValidator.hasDecimalOrTrailingZeroes(input)) {
                printError("Invalid format: Decimals or trailing zeroes (e.g. '1.0' or '5.00') are not permitted. Please enter a whole integer.");
                continue;
            }
            if (InputValidator.hasLeadingZeroes(input)) {
                printError("Invalid format: Leading zeroes (e.g. '01') are not permitted. Please enter standard digits.");
                continue;
            }
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
                printError("Please enter a number between " + min + " and " + max + ".");
            } catch (NumberFormatException e) {
                printError("Invalid number format. Please enter an integer.");
            }
        }
    }

    public static int readCancellablePositiveInt(String prompt) {
        return readCancellablePositiveInt(prompt, 50000);
    }

    /**
     * Reads a whole number from 1 to maxLimit.
     *
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static int readCancellablePositiveInt(String prompt, int maxLimit) {
        while (true) {
            String input = unlessCancelled(readString(prompt + " (or type 'cancel' / '0' to abort)"));
            if (isBlank(input)) {
                printError("This field cannot be empty. Please enter a positive integer.");
                continue;
            }
            if (InputValidator.hasDecimalOrTrailingZeroes(input)) {
                printError("Invalid format: Decimals or trailing zeroes (e.g. '10.0') are not permitted. Please enter a whole integer.");
                continue;
            }
            if (InputValidator.hasLeadingZeroes(input)) {
                printError("Invalid format: Leading zeroes (e.g. '05') are not permitted.");
                continue;
            }
            try {
                int val = Integer.parseInt(input);
                if (val <= 0) {
                    printError("Value must be a positive number greater than 0.");
                    continue;
                }
                if (val > maxLimit) {
                    printError("Value exceeds maximum allowable limit (" + maxLimit + ").");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                printError("Invalid number format. Please enter an integer.");
            }
        }
    }

    public static int readOptionalPositiveInt(String prompt, int defaultValue) {
        while (true) {
            String input = readString(prompt + " [" + defaultValue + "]");
            if (isBlank(input)) {
                return defaultValue;
            }
            if (InputValidator.hasDecimalOrTrailingZeroes(input)) {
                printError("Invalid format: Decimals or trailing zeroes (e.g. '10.0') are not permitted.");
                continue;
            }
            if (InputValidator.hasLeadingZeroes(input)) {
                printError("Invalid format: Leading zeroes (e.g. '05') are not permitted.");
                continue;
            }
            try {
                int val = Integer.parseInt(input);
                if (val <= 0) {
                    printError("Value must be greater than 0.");
                    continue;
                }
                if (val > 50000) {
                    printError("Value exceeds maximum allowable limit (50,000).");
                    continue;
                }
                return val;
            } catch (NumberFormatException e) {
                printError("Invalid number format.");
            }
        }
    }

    public static boolean readConfirmation(String prompt) {
        while (true) {
            String val = readString(prompt + " (y/n)").toLowerCase();
            if (val.equals("y") || val.equals("yes")) return true;
            if (val.equals("n") || val.equals("no")) return false;
            printError("Please enter 'y' for yes or 'n' for no.");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static LocalDateTime readDateTime(String prompt, boolean mustBeFuture) {
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (or 'cancel' to abort, e.g. 2026-11-20 14:30)" + RESET);
            String input = unlessCancelled(readNonEmptyString(prompt));
            try {
                LocalDateTime dt = LocalDateTime.parse(input, DATE_TIME_FORMATTER);
                if (mustBeFuture && dt.isBefore(LocalDateTime.now())) {
                    printError("Event date/time must be in the future (current time is " +
                            LocalDateTime.now().format(DATE_TIME_FORMATTER) + ").");
                    continue;
                }
                if (mustBeFuture && !InputValidator.isWithinMaxHorizon(dt)) {
                    printError("Event date/time cannot be scheduled more than " +
                            InputValidator.MAX_EVENT_HORIZON_YEARS + " years in advance.");
                    continue;
                }
                return dt;
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static LocalDateTime readDateTimeAfter(String prompt, LocalDateTime afterDateTime) {
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (or 'cancel' to abort, must be after " +
                    afterDateTime.format(DATE_TIME_FORMATTER) + ")" + RESET);
            String input = unlessCancelled(readNonEmptyString(prompt));
            try {
                LocalDateTime dt = LocalDateTime.parse(input, DATE_TIME_FORMATTER);
                if (!dt.isAfter(afterDateTime)) {
                    printError("End date/time must be strictly after the start date/time (" +
                            afterDateTime.format(DATE_TIME_FORMATTER) + ").");
                    continue;
                }
                return dt;
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    public static LocalDateTime readOptionalDateTime(String prompt, LocalDateTime defaultValue, boolean mustBeFuture) {
        String defaultStr = defaultValue != null ? defaultValue.format(DATE_TIME_FORMATTER) : "none";
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (leave empty to keep [" + defaultStr + "])" + RESET);
            String input = readString(prompt);
            if (isBlank(input)) {
                return defaultValue;
            }
            try {
                LocalDateTime dt = LocalDateTime.parse(input, DATE_TIME_FORMATTER);
                if (mustBeFuture && dt.isBefore(LocalDateTime.now())) {
                    printError("Date/time must be in the future (current time is " +
                            LocalDateTime.now().format(DATE_TIME_FORMATTER) + ").");
                    continue;
                }
                if (mustBeFuture && !InputValidator.isWithinMaxHorizon(dt)) {
                    printError("Date/time cannot be scheduled more than " +
                            InputValidator.MAX_EVENT_HORIZON_YEARS + " years in advance.");
                    continue;
                }
                return dt;
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    public static LocalDateTime readOptionalDateTimeAfter(String prompt, LocalDateTime defaultValue, LocalDateTime minDateTime) {
        String defaultStr = defaultValue != null ? defaultValue.format(DATE_TIME_FORMATTER) : "none";
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (leave empty to keep [" + defaultStr + "])" + RESET);
            String input = readString(prompt);
            if (isBlank(input)) {
                return defaultValue;
            }
            try {
                LocalDateTime dt = LocalDateTime.parse(input, DATE_TIME_FORMATTER);
                if (minDateTime != null && !dt.isAfter(minDateTime)) {
                    printError("End date/time must be strictly after the start date/time (" +
                            minDateTime.format(DATE_TIME_FORMATTER) + ").");
                    continue;
                }
                return dt;
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readEmail(String prompt) {
        while (true) {
            String email = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to return)"));
            if (InputValidator.isValidEmail(email)) {
                return email;
            }
            printError("Invalid email format (example: name@domain.com). Please try again.");
        }
    }

    public static String readOptionalEmail(String prompt, String defaultValue) {
        String promptWithDefault = isBlank(defaultValue)
                ? prompt
                : prompt + " [" + defaultValue + "]";
        while (true) {
            String val = readString(promptWithDefault);
            if (isBlank(val) && defaultValue != null) {
                return defaultValue;
            }
            if (InputValidator.isValidEmail(val)) {
                return val.trim();
            }
            printError("Invalid email format (example: name@domain.com). Please try again.");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readUsername(String prompt) {
        while (true) {
            String user = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to return)"));
            if (InputValidator.isValidUsername(user)) {
                return user.trim();
            }
            printError("Username must be 3-30 characters long and contain only letters, numbers, underscores, dots, or hyphens.");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readPassword(String prompt) {
        while (true) {
            String pass = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to return)"));
            if (InputValidator.isValidPassword(pass)) {
                return pass;
            }
            printError("Password must be between 6 and 64 characters long, contain at least one letter and one number, and cannot consist solely of whitespace.");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readFullName(String prompt) {
        while (true) {
            String name = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to return)"));
            if (InputValidator.isValidFullName(name)) {
                return name.trim();
            }
            printError("Please enter a valid full name (2-50 characters, letters and spaces only).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readEventId(String prompt) {
        while (true) {
            String id = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' / '0' to return)"));
            if (InputValidator.isValidEventId(id)) {
                return id.trim();
            }
            printError("Invalid Event ID format (must match 'EVT-XXXX', e.g. EVT-1001, or '0' to cancel).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readUserId(String prompt) {
        while (true) {
            String id = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' / '0' to return)"));
            if (InputValidator.isValidUserId(id)) {
                return id.trim();
            }
            printError("Invalid User ID format (must match 'USR-XXXX', e.g. USR-1004, or '0' to cancel).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readRegistrationId(String prompt) {
        while (true) {
            String id = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' / '0' to return)"));
            if (InputValidator.isValidRegistrationId(id)) {
                return id.trim();
            }
            printError("Invalid Registration ID format (must match 'REG-XXXX', e.g. REG-1004, or '0' to cancel).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readCategoryId(String prompt) {
        while (true) {
            String id = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' / '0' to return)"));
            if (InputValidator.isValidCategoryId(id)) {
                return id.trim();
            }
            printError("Invalid Category ID format (must match 'CAT-XXX', e.g. CAT-101, or '0' to cancel).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readCancellableEventTitle(String prompt) {
        while (true) {
            String val = unlessCancelled(readString(prompt + " (or type 'cancel' / '0' to abort)").trim());
            if (InputValidator.isValidEventTitle(val)) {
                return InputValidator.sanitizeSingleLine(val);
            }
            printError("Event title must be between 3 and 100 characters long (or type 'cancel' to exit).");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readVenue(String prompt) {
        while (true) {
            String val = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to abort)").trim());
            if (InputValidator.isValidVenue(val)) {
                return InputValidator.sanitizeSingleLine(val);
            }
            printError("Venue / Room must be between 2 and 100 characters long.");
        }
    }

    public static String readDescription(String prompt, int maxLen) {
        while (true) {
            String val = readString(prompt + " (optional, max " + maxLen + " chars)");
            if (isBlank(val)) {
                return "";
            }
            if (val.trim().length() <= maxLen) {
                return InputValidator.sanitizeSingleLine(val);
            }
            printError("Description cannot exceed " + maxLen + " characters.");
        }
    }

    /**
     * @throws OperationCancelledException if the user types a cancel token
     */
    public static String readComment(String prompt) {
        while (true) {
            String val = unlessCancelled(readNonEmptyString(prompt + " (or type 'cancel' to abort)"));
            if (InputValidator.isValidComment(val)) {
                return InputValidator.sanitizeSingleLine(val);
            }
            printError("Review comment must be between 3 and 1,000 characters and cannot consist solely of repetitive characters or spam.");
        }
    }

    public static void pause() {
        System.out.println();
        System.out.print(DIM + "Press [Enter] to continue..." + RESET);
        if (scanner.hasNextLine()) {
            scanner.nextLine();
        }
    }

    public static String centerText(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int totalPad = width - text.length();
        int padLeft = totalPad / 2;
        int padRight = totalPad - padLeft;
        return repeat(" ", padLeft) + text + repeat(" ", padRight);
    }
}
