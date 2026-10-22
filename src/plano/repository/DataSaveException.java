package plano.repository;

/**
 * Thrown when a data file cannot be written. The change is still in memory but not on disk,
 * so the menus show this as an error instead of the operation's success message.
 */
public class DataSaveException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataSaveException(String filePath, String problem) {
        super("Could not save " + filePath + " (" + problem + "). The change is kept until PlanO closes, "
                + "but it is not saved and will be lost unless a later save succeeds.");
    }
}
