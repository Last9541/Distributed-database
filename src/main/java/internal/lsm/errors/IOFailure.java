package internal.lsm.errors;

public class IOFailure extends LsmvkException{


    public IOFailure() {
    }

    public IOFailure(String message) {
        super(message);
    }

    @Override
    public String toString() {
        return "IOFailure"+super.toString();
    }
}
