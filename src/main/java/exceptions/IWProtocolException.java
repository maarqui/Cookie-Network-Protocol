package exceptions;

public abstract class IWProtocolException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7002466204521265834L;

    // default constructor
    public IWProtocolException() {
        super();
    }

    // constructor with message
    public IWProtocolException(String message) {
        super(message);
    }

}
