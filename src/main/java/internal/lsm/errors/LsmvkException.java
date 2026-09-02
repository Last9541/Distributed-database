package internal.lsm.errors;

public class LsmvkException extends RuntimeException{

    public LsmvkException() {
    }

    public LsmvkException(String message) {
        super(message);
    }

    public String toString()
    {
        String txt="";
        if(this.getMessage()!=null)
            txt+=" "+this.getMessage();
        return txt;
    }
}
