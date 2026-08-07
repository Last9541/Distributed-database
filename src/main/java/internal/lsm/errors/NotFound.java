package internal.lsm.errors;

import java.util.StringJoiner;

public class NotFound extends RuntimeException {
    @Override
    public String toString() {
       return "NotFound";
    }
}
