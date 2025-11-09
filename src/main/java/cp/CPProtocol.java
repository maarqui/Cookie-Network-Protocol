package cp;

import core.*;
import exceptions.*;
import phy.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class CPProtocol extends Protocol {
    private static final int CP_TIMEOUT = 2000;
    private static final int CP_HASHMAP_SIZE = 20;
    private int cookie;
    private int id;
    private PhyConfiguration PhyConfigCommandServer;
    private PhyConfiguration PhyConfigCookieServer;
    private final PhyProtocol PhyProto;
    private final cp_role role;
    HashMap<PhyConfiguration, Cookie> cookieMap;
    ArrayList<CPCommandMsg> pendingCommands;
    Random rnd;
    private CPCommandMsg lastSentCommand;

    private enum cp_role {
        CLIENT, COOKIE, COMMAND
    }

    // Constructor for clients
    public CPProtocol(InetAddress rname, int rp, PhyProtocol phyP) throws UnknownHostException {
        this.PhyConfigCommandServer = new PhyConfiguration(rname, rp, proto_id.CP);
        this.PhyProto = phyP;
        this.role = cp_role.CLIENT;
        this.cookie = -1;
    }
    // Constructor for servers
    public CPProtocol(PhyProtocol phyP, boolean isCookieServer) {
        this.PhyProto = phyP;
        if (isCookieServer) {
            this.role = cp_role.COOKIE;
            this.cookieMap = new HashMap<>();
            this.rnd = new Random();
        } else {
            this.role = cp_role.COMMAND;
            this.pendingCommands = new ArrayList<>();
        }
    }

    public void setCookieServer(InetAddress rname, int rp) throws UnknownHostException {
        this.PhyConfigCookieServer = new PhyConfiguration(rname, rp, proto_id.CP);
    }


    @Override
    public void send(String s, Configuration config) throws IOException, IWProtocolException {
        CPCommandMsg msg= new CPCommandMsg();

        if (cookie < 0) {
            // Request a new cookie from server
            // Either updates the cookie attribute or returns with an exception
            requestCookie();
        }

        // Task 1.2.1: complete send method

    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        if (this.lastSentCommand == null) {
            // not supposed to happen if send() is called before
            throw new IWProtocolException();
            // "receive() called before send()"
        }

        // Implement receive method:

        // Wait 2s (CP_TIMEOUT) for the server response
        try {
            // call receive from the physical layer
            Msg in = this.PhyProto.receive(CP_TIMEOUT);

            // validate that the message is from the correct protocol (CP)
            if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) {
                // Si no, ignorar y re-intentar... pero por simplicidad
                // lanzamos una excepción o devolvemos null.
                // Aquí lanzamos excepción.
                throw new IWProtocolException();
                // "Received message for wrong protocol"
            }

            // call parser
            CPMsg cpmIn = new CPMsg(); // temporal object to call parse() from CPMsg
            cpmIn = (CPMsg) cpmIn.parse(in.getData());

            // verify if response is CPCommandResponseMsg
            if (!(cpmIn instanceof CPCommandResponseMsg)) {
                throw new IWProtocolException();
                // "Unexpected message type received"
            }

            CPCommandResponseMsg response = (CPCommandResponseMsg) cpmIn;

            // verify ID
            if (response.getId() != this.lastSentCommand.getId()) {
                throw new IWProtocolException();
                // "Response ID mismatch"
            }

            // verify success
            if (!response.isSuccess()) {
                throw new IWProtocolException();
                // "Server rejected command: " + response.getResponseMessage()
            }

            // return to client:
            // success. data message is returned by CPClient.
            // rewrite 'data' for 'message' (the one that the client wants to see)
            if (response.getResponseMessage().isEmpty()) {
                response.setData("Server: OK");
            } else {
                response.setData(response.getResponseMessage());
            }

            return response;

        } catch (SocketTimeoutException e) {
            // the server didnt respond in CP_TIMEOUT time
            throw new IWProtocolException();
            // "Server timeout"
        }
    }

    // CookieServer processing of incoming messages
    // Only CookieCommandMsg are processed, all others are ignored
    private Msg command_process(CPMsg cpmIn) throws IWProtocolException {
        CPCommandMsg stored = null;
        Msg msg = null;     // Placeholder

        return msg;
    }


    // Processing of the CookieRequestMsg
    private void cookie_process(CPMsg cpmIn) throws IWProtocolException, IOException {

    }


    // Method for the client to request a cookie
    public void requestCookie() throws IOException, IWProtocolException {
        CPCookieRequestMsg reqMsg = new CPCookieRequestMsg();
        reqMsg.create(null);
        Msg resMsg = new CPMsg();

        boolean waitForResp = true;
        int count = 0;
        while(waitForResp && count < 3) {
            this.PhyProto.send(new String(reqMsg.getDataBytes()), this.PhyConfigCookieServer);

            try {
                Msg in = this.PhyProto.receive(CP_TIMEOUT);
                if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP)
                    continue;
                resMsg = ((CPMsg) resMsg).parse(in.getData());
                if(resMsg instanceof CPCookieResponseMsg)
                    waitForResp = false;
            } catch (SocketTimeoutException e) {
                count += 1;
            } catch (IWProtocolException ignored) {
            }
        }

        if(count == 3)
            throw new CookieRequestException();
        if(resMsg instanceof CPCookieResponseMsg && !((CPCookieResponseMsg) resMsg).getSuccess()) {
            throw new CookieRequestException();
        }
         assert resMsg instanceof CPCookieResponseMsg;
         this.cookie = ((CPCookieResponseMsg)resMsg).getCookie();
    }
}

class Cookie {
    private final long timeOfCreation;
    private final int cookieValue;

    public Cookie(long toc, int c) {
        this.timeOfCreation = toc;
        this.cookieValue = c;
    }

    public long getTimeOfCreation() {
        return timeOfCreation;
    }

    public int getCookieValue() { return cookieValue;}
}

