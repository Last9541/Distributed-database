package internal.lsm.errors;

public class CorruptionDetected extends LsmvkException{

    public CorruptionDetected(String message) {
        super(message);
    }

    public CorruptionDetected() {
    }

    @Override
    public String toString() {
        return "CorruptionDetected"+super.toString();
    }
}
