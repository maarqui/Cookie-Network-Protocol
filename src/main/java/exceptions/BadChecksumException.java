package exceptions;

public class BadChecksumException extends IWProtocolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6481772554012497583L;

    public BadChecksumException() {
        super();
    }

    public BadChecksumException(String message) {
        super(message);
    }

}
