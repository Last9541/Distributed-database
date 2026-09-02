package internal.lsm.errors;

import java.util.StringJoiner;

public class NotFound extends LsmvkException {

    public NotFound() {
    }

    public NotFound(String message) {
        super(message);
    }

    @Override
    public String toString() {
       return "NotFound"+super.toString();
    }
}
