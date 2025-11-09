package exceptions;

public class IllegalMsgException extends IWProtocolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2003522098885060854L;

    public IllegalMsgException() {
        super();
    }

    public IllegalMsgException(String message) {
        super(message);
    }
}
