package exceptions;

public class RegistrationFailedException extends IWProtocolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3028142631363949563L;

    public RegistrationFailedException() {
        super();
    }

    public RegistrationFailedException(String message) {
        super(message);
    }

}
