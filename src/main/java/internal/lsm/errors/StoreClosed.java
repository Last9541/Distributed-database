package internal.lsm.errors;

public class StoreClosed extends RuntimeException{
    @Override
    public String toString() {
        return "StoreClosed";
    }
}
