package plano.service;

/**
 * The outcome of an operation that has nothing to return but a message:
 * whether it worked, and what to tell the user. Both fields are final,
 * so a result cannot be changed after it is created.
 */
public class OperationResult {
    public final boolean success;
    public final String message;

    public OperationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}
