package internal.lsm.errors;

public class NotImplemented extends LsmvkException{

    public NotImplemented() {
    }

    public NotImplemented(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "NotImplemented"+super.toString();
    }
}
