package cp;

import core.Msg;
import exceptions.IllegalMsgException;

 // format: cp cookie_verification_response <ok|error>
public class CPCookieVerificationResponseMsg extends CPMsg {
    private boolean success;
    protected static final String VERIFY_RES_HEADER = "cookie_verification_response";

    public void create(boolean success) {
        this.success = success;
        String status = success ? "ok" : "error";
        super.create(VERIFY_RES_HEADER + " " + status);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        if (!sentence.startsWith(VERIFY_RES_HEADER)) {
            throw new IllegalMsgException("Not a verification response");
        }
        String[] parts = sentence.split("\\s+");
        this.success = parts[1].equals("ok");
        return this;
    }

    public boolean isSuccess() { return success; }
}