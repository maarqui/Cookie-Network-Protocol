package exceptions;

public class CookieTimeoutException extends IWProtocolException {

    public CookieTimeoutException() {
        super();
    }

    public CookieTimeoutException(String message) {
        super(message);
    }
}


