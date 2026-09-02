package internal.lsm.errors;

public class StoreClosed extends LsmvkException{
    public StoreClosed(String message) {
        super(message);
    }

    public StoreClosed() {
    }

    @Override
    public String toString() {
        return "StoreClosed"+super.toString();
    }
}
