package cp;

import core.Msg;
import exceptions.IWProtocolException;
import exceptions.IllegalMsgException;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CPMsg extends Msg {
    protected static final String CP_HEADER = "cp";
    @Override
    protected void create(String sentence) {
        data = CP_HEADER + " " + sentence;
        this.dataBytes = data.getBytes();
    }

    @Override
    protected Msg parse(String sentence) throws IWProtocolException {
        CPMsg parsedMsg;
        // validate "cp" prefix
        if(!sentence.startsWith(CP_HEADER)) throw new IllegalMsgException();

        // separate prefix from body
        String[] parts = sentence.split("\\s+", 2);
        if(parts.length < 2) throw new IllegalMsgException();

        // check header
        String body = parts[1];
        if (body.startsWith(CPCommandResponseMsg.CP_CMD_RES_HEADER)) {
            parsedMsg = new CPCommandResponseMsg();
        } else if (body.startsWith(CPCommandMsg.CP_CMD_HEADER)) {
            parsedMsg = new CPCommandMsg();
        } else if (body.startsWith(CPCookieResponseMsg.CP_CRES_HEADER)) {
            parsedMsg = new CPCookieResponseMsg();
        } else if (body.startsWith(CPCookieRequestMsg.CP_CREQ_HEADER)) {
            parsedMsg = new CPCookieRequestMsg();
        } else if (body.startsWith(CPCookieVerificationResponseMsg.VERIFY_RES_HEADER)) {
            parsedMsg = new CPCookieVerificationResponseMsg();
        } else if (body.startsWith(CPCookieVerificationRequestMsg.VERIFY_REQ_HEADER)) {
            parsedMsg = new CPCookieVerificationRequestMsg();
        } else {
            throw new IllegalMsgException("Unknown message header: " + body);
        }

        // return parse to the corresponding class
        return (CPMsg) parsedMsg.parse(body);
    }

}
