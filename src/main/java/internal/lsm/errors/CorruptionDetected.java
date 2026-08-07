package internal.lsm.errors;

public class CorruptionDetected extends RuntimeException{
    @Override
    public String toString() {
        return "CorruptionDetected";
    }
}
