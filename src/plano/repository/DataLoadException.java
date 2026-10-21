package plano.repository;

/**
 * Thrown when a data file cannot be read. PlanO stops instead of starting with
 * partial data, so a later save can never overwrite records that failed to load.
 */
public class DataLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String filePath;

    public DataLoadException(String filePath, String problem) {
        super("Could not load " + filePath + ": " + problem + ". The data files were left unchanged. "
                + "Fix the file, or delete the whole data folder to start again with fresh demo data.");
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }
}
