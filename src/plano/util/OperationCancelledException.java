package plano.util;

/**
 * Thrown when the user types 'cancel', '0' or 'back' at a cancellable prompt.
 * It unwinds the current workflow back to the menu, which catches it in one place,
 * so individual workflows never need to check for a cancelled input.
 */
public class OperationCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OperationCancelledException() {
        super("Operation cancelled by user.");
    }
}
