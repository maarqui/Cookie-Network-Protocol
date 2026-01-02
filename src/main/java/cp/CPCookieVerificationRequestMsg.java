package cp;

import core.Msg;
import exceptions.IllegalMsgException;

 // format: cp cookie_verification_request <cookie>
public class CPCookieVerificationRequestMsg extends CPMsg {
    private int cookieValue;
    protected static final String VERIFY_REQ_HEADER = "cookie_verification_request";

    public void create(int cookie) {
        this.cookieValue = cookie;
        //  "cp" prefix is added by a higher layer (CPMsg class)
        String finalMsg = VERIFY_REQ_HEADER + " " + cookie;
        super.create(finalMsg);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        if (!sentence.startsWith(VERIFY_REQ_HEADER)) {
            throw new IllegalMsgException("Not a verification request");
        }
        String[] parts = sentence.split("\\s+");
        if (parts.length < 2) throw new IllegalMsgException("Missing cookie value");

        this.cookieValue = Integer.parseInt(parts[1]);
        return this;
    }

    public int getCookieValue() { return cookieValue; }
}