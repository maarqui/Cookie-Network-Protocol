package exceptions;

public class IllegalCommandException extends IWProtocolException {

    public IllegalCommandException() {
        super();
    }

    public IllegalCommandException(String message) {
        super(message);
    }
}
