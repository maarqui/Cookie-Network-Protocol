package exceptions;

import exceptions.IWProtocolException;

public class IllegalAddrException extends IWProtocolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2112808760774454967L;

    public IllegalAddrException() {
        super();
    }

    public IllegalAddrException(String message) {
        super(message);
    }
}
