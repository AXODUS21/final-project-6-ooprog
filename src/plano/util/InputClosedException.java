package plano.util;

/**
 * Thrown when the console input stream has ended (for example after Ctrl+Z / Ctrl+D or
 * when piped input runs out), so the application can exit instead of re-prompting forever.
 */
public class InputClosedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InputClosedException() {
        super("Console input has ended.");
    }
}
