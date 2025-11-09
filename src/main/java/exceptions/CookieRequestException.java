package exceptions;

public class CookieRequestException extends IWProtocolException {

    public CookieRequestException() {
        super();
    }

    public CookieRequestException(String message) {
        super(message);
    }
}
