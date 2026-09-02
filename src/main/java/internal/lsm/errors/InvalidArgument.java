package internal.lsm.errors;

public class InvalidArgument extends LsmvkException{

    public InvalidArgument() {
    }

    public InvalidArgument(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "InvalidArgument"+super.toString();
    }
}
