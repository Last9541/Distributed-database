package internal.lsm.errors;

public class InvalidArgument extends RuntimeException{

    public InvalidArgument() {
    }

    public InvalidArgument(String message) {
        super(message);
    }

    @Override
    public String toString() {
        String txt="InvalidArgument";
        if(this.getMessage()!=null)
            txt+=" "+this.getMessage();
        return txt;
    }
}
