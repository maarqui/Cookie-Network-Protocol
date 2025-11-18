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
    private static final int CP_TIMEOUT = 3000;
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
        // 's' is the raw user command

        if (cookie < 0) {
            // Request a new cookie from server
            // Either updates the cookie attribute or returns with an exception
            requestCookie();
        }

        // increment ID
        this.id++;
        // manage overflow
        if (this.id > 65535) {
            this.id = 1; // restart in case the max is reached
        }

        // create CPCommandMsg object to create the message
        CPCommandMsg msg = new CPCommandMsg();
        msg.create(s, this.id, this.cookie);

        // save sent message for verification
        this.lastSentCommand = msg;

        // send the command to the command server
        this.PhyProto.send(new String(msg.getDataBytes()), this.PhyConfigCommandServer);
    }

    @Override
    public Msg receive() throws IOException, IWProtocolException {
        // Ramification of server/client logics:
        if(this.role == cp_role.CLIENT) {
            // Client logic:
            if (this.lastSentCommand == null) {
                // not supposed to happen if send() is called before
                throw new NoNextStateException("receive() called before send()");
            }

            int count = 0;
            while (count < 3) {
                try {
                    // call receive from the physical layer
                    Msg in = this.PhyProto.receive(CP_TIMEOUT);

                    // validate that the message is from the correct protocol (CP)
                    if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) {
                        continue; // package for a different protocol, ignore and keep waiting
                    }

                    // call parser
                    CPMsg cpmIn = new CPMsg();
                    cpmIn = (CPMsg) cpmIn.parse(in.getData());

                    // verify if response is CPCommandResponseMsg
                    if (!(cpmIn instanceof CPCommandResponseMsg response)) {
                        continue; // not a command response, ignore
                    }

                    // verify ID
                    if (response.getId() != this.lastSentCommand.getId()) {
                        continue; // If incorrect ID wait for the correct response
                    }

                    // verify success
                    if (!response.isSuccess()) {
                        // if the server returns error, throw exception
                        throw new IllegalCommandException("Server rejected command: " + response.getResponseMessage());
                    }

                    // return to client
                    if (response.getResponseMessage().isEmpty()) {
                        response.setData("Server: OK");
                    } else {
                        response.setData(response.getResponseMessage());
                    }

                    return response; // return exit response

                } catch (SocketTimeoutException e) {
                    // if timeout exception is reached increment count and continue
                    count += 1;
                } catch (IllegalMsgException e) {
                    // catches illegal message exceptions
                }
            }
            // if loop ends, throw server timeout
            throw new CookieTimeoutException("Server timeout (3 attempts of 3s each)");
        }else {
            // Server logic:
            Msg in = this.PhyProto.receive();

            // validate if package is for this protocol
            if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) {
                return null; // if not, ignore package
            }

            // parse message
            CPMsg cpmIn;
            try {
                CPMsg parser = new CPMsg();
                cpmIn = (CPMsg) parser.parse(in.getData());
                cpmIn.setConfiguration(in.getConfiguration());
            } catch (IWProtocolException e) {
                return null;
            }

            // ramification of processing methods:
            if (this.role == cp_role.COOKIE) {
                // cookie server processing method
                if (cpmIn instanceof CPCookieRequestMsg) {
                    // process cookie
                    cookie_process(cpmIn);
                }

            } else if (this.role == cp_role.COMMAND) {
                // command server processing method
                if (cpmIn instanceof CPCommandMsg) {
                    // process command
                    // TODO: command_process(cpmIn);
                }
            }
            return cpmIn; // return the message
        }
    }

    // Processing of the CookieRequestMsg
    private void cookie_process(CPMsg cpmIn) throws IWProtocolException, IOException {
        // Obtain client config:
        PhyConfiguration clientConfig = (PhyConfiguration) cpmIn.getConfiguration();
        CPCookieResponseMsg resMsg;

        // if server full, reject request:
        if (cookieMap.size() >= CP_HASHMAP_SIZE) {
            resMsg = new CPCookieResponseMsg(false); // success = false
            resMsg.create("Server full");
        } else {

            // If client already exists, their cookie overwrites invalidating the old one:
            // generate new cookie
            int newCookieValue = rnd.nextInt(Integer.MAX_VALUE);
            Cookie cookie = new Cookie(System.currentTimeMillis(), newCookieValue);

            // store new cookie with put() so it overwrites if one already exists for that client.
            cookieMap.put(clientConfig, cookie);

            // return ACK
            resMsg = new CPCookieResponseMsg(true); // success = true
            resMsg.create("New cookie created: " + newCookieValue);
        }

        // return the response to the client
        this.PhyProto.send(new String(resMsg.getDataBytes()), clientConfig);
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

