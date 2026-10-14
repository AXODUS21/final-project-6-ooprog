package plano.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/**
 * Handles terminal I/O, ANSI colors, formatted banners, and safe user input prompts.
 */
public class ConsoleIO {
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String UNDERLINE = "\u001B[4m";

    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_MAGENTA = "\u001B[45m";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");

    private static final Scanner scanner = new Scanner(System.in);

    public static void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void printBanner(String title, String subtitle) {
        int width = 76;
        String line = "═".repeat(width);
        System.out.println(CYAN + BOLD + "╔" + line + "╗" + RESET);
        System.out.println(CYAN + BOLD + "║" + centerText(title, width) + "║" + RESET);
        if (subtitle != null && !subtitle.isBlank()) {
            System.out.println(CYAN + "║" + centerText(subtitle, width) + "║" + RESET);
        }
        System.out.println(CYAN + BOLD + "╚" + line + "╝" + RESET);
    }

    public static void printSection(String title) {
        System.out.println();
        System.out.println(BOLD + BLUE + "▶ " + title.toUpperCase() + RESET);
        System.out.println(DIM + "─".repeat(Math.max(40, title.length() + 4)) + RESET);
    }

    public static void printSuccess(String message) {
        System.out.println(GREEN + BOLD + "✓ " + RESET + GREEN + message + RESET);
    }

    public static void printError(String message) {
        System.out.println(RED + BOLD + "✗ Error: " + RESET + RED + message + RESET);
    }

    public static void printWarning(String message) {
        System.out.println(YELLOW + BOLD + "⚠ Warning: " + RESET + YELLOW + message + RESET);
    }

    public static void printInfo(String message) {
        System.out.println(CYAN + "ℹ " + message + RESET);
    }

    public static String readString(String prompt) {
        System.out.print(BOLD + prompt + ": " + RESET);
        if (scanner.hasNextLine()) {
            return scanner.nextLine().trim();
        }
        return "";
    }

    public static String readNonEmptyString(String prompt) {
        while (true) {
            String val = readString(prompt);
            if (!val.isBlank()) {
                return val;
            }
            printError("This field cannot be empty. Please enter a valid value.");
        }
    }

    public static String readOptionalString(String prompt, String defaultValue) {
        String promptWithDefault = defaultValue == null || defaultValue.isBlank()
                ? prompt
                : prompt + " [" + defaultValue + "]";
        String val = readString(promptWithDefault);
        if (val.isBlank() && defaultValue != null) {
            return defaultValue;
        }
        return val;
    }

    public static int readInt(String prompt, int min, int max) {
        while (true) {
            String input = readString(prompt + " (" + min + " - " + max + ")");
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

    public static int readPositiveInt(String prompt) {
        while (true) {
            String input = readString(prompt);
            try {
                int val = Integer.parseInt(input);
                if (val > 0) {
                    return val;
                }
                printError("Value must be a positive number greater than 0.");
            } catch (NumberFormatException e) {
                printError("Invalid number format. Please enter an integer.");
            }
        }
    }

    public static int readOptionalPositiveInt(String prompt, int defaultValue) {
        while (true) {
            String input = readString(prompt + " [" + defaultValue + "]");
            if (input.isBlank()) {
                return defaultValue;
            }
            try {
                int val = Integer.parseInt(input);
                if (val > 0) {
                    return val;
                }
                printError("Value must be greater than 0.");
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

    public static LocalDateTime readDateTime(String prompt, boolean mustBeFuture) {
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (e.g. 2026-11-20 14:30)" + RESET);
            String input = readNonEmptyString(prompt);
            try {
                LocalDateTime dt = LocalDateTime.parse(input, DATE_TIME_FORMATTER);
                if (mustBeFuture && dt.isBefore(LocalDateTime.now())) {
                    printError("Event date/time must be in the future (current time is " +
                            LocalDateTime.now().format(DATE_TIME_FORMATTER) + ").");
                    continue;
                }
                return dt;
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    public static LocalDateTime readOptionalDateTime(String prompt, LocalDateTime defaultValue) {
        String defaultStr = defaultValue != null ? defaultValue.format(DATE_TIME_FORMATTER) : "none";
        while (true) {
            System.out.println(DIM + "Format: yyyy-MM-dd HH:mm (leave empty to keep [" + defaultStr + "])" + RESET);
            String input = readString(prompt);
            if (input.isBlank()) {
                return defaultValue;
            }
            try {
                return LocalDateTime.parse(input, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e) {
                printError("Invalid date/time format. Please use format: yyyy-MM-dd HH:mm");
            }
        }
    }

    public static String readEmail(String prompt) {
        while (true) {
            String email = readNonEmptyString(prompt);
            if (InputValidator.isValidEmail(email)) {
                return email;
            }
            printError("Invalid email format (example: name@domain.com). Please try again.");
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
        return " ".repeat(padLeft) + text + " ".repeat(padRight);
    }
}
