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
        if(!sentence.startsWith(CP_HEADER)) throw new IllegalMsgException();

        String[] parts = sentence.split("\\s+", 2);
        if(parts.length < 2) throw new IllegalMsgException();

        if(parts[1].startsWith(CPCookieRequestMsg.CP_CREQ_HEADER)) {
            parsedMsg = new CPCookieRequestMsg();
        } else if(parts[1].startsWith(CPCookieResponseMsg.CP_CRES_HEADER)) {
            parsedMsg = new CPCookieResponseMsg();
        } else if(parts[1].startsWith(CPCommandMsg.CP_CMD_HEADER)) {
            parsedMsg = new CPCommandMsg();
        } else if(parts[1].startsWith(CPCommandResponseMsg.CP_CMD_RES_HEADER)) {
            parsedMsg = new CPCommandResponseMsg();
        } else if (parts[1].startsWith(CPCookieVerificationRequestMsg.VERIFY_REQ_HEADER)){
            parsedMsg = new CPCommandResponseMsg();
        }else if(parts[1].startsWith(CPCookieVerificationResponseMsg.VERIFY_RES_HEADER)){
            parsedMsg = new CPCommandResponseMsg();
        }else {
            throw new IllegalMsgException();
        }

        parsedMsg = (CPMsg) parsedMsg.parse(parts[1]);
        return parsedMsg;
    }

}
