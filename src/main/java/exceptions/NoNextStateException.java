package exceptions;

public class NoNextStateException extends IWProtocolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 9032871496863489383L;

    public NoNextStateException() {
        super();
    }

    public NoNextStateException(String message) {
        super(message);
    }
}
