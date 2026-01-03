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
        this.id = -1;
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
            this.id = 0; // restart in case the max is reached
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
            int count = 0;
            while (count < 3) {
                try {
                    // call receive from the physical layer
                    Msg in = this.PhyProto.receive(CP_TIMEOUT);

                    // validation of corrupt packages sent by PhyProtocol in case of exception
                    if (in == null) {
                        System.out.println("attempt " + count + " - nothing received (null)");
                        count++;
                        continue;
                    }

                    System.out.println("received data: " + in.getData());

                    // validate that the message is from the correct protocol (CP)
                    if (((PhyConfiguration) in.getConfiguration()).getPid() != proto_id.CP) {
                        System.out.println("incorrect PID: " + ((PhyConfiguration) in.getConfiguration()).getPid());
                        continue; // package for a different protocol, ignore and keep waiting
                    }

                    // call parser
                    CPMsg cpmIn = new CPMsg();
                    cpmIn = (CPMsg) cpmIn.parse(in.getData());
                    System.out.println("parsed class: " + cpmIn.getClass().getSimpleName());

                    // verify if response is CPCommandResponseMsg instance
                    if (!(cpmIn instanceof CPCommandResponseMsg response)) {
                        System.out.println("Error - message is not instance of CPCommandResponseMsg");
                        count++;
                        continue; // not a command response, ignore
                    }

                    // verify ID if command is not null
                    if (lastSentCommand != null && response.getId() != this.lastSentCommand.getId()) {
                        System.out.println("ID Mismatch - expected: " + lastSentCommand.getId() + " received: " + response.getId());
                        count++;
                        continue; // if incorrect ID wait for the correct response
                    }

                    // verify success
                    if (!response.isSuccess()) {
                        System.out.println("server responded with ERROR: " + response.getResponseMessage());
                        throw new CookieTimeoutException(response.getResponseMessage());
                    }

                    // return to client
                    System.out.println("SUCCESS! returning answer.");
                    return response; // return exit response

                } catch (SocketTimeoutException e) {
                    // if timeout exception is reached increment count and continue
                    System.out.println("timeout in attempt " + count);
                    count++;
                } catch (IllegalMsgException e) {
                    // catches illegal message exceptions
                    System.out.println("corrupt message: " + e.getMessage());
                    count++;
                }
            }
            // if loop ends, throw server timeout
            throw new CookieTimeoutException("Server timeout");
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

            // ramification of server processing methods:
            if (this.role == cp_role.COOKIE) {
                // cookie server processing method
                if (cpmIn instanceof CPCookieRequestMsg) {
                    // process cookie
                    cookieProcess(cpmIn);
                }else if (cpmIn instanceof CPCookieVerificationRequestMsg){
                    handleVerificationRequest(cpmIn);
                }

            } else if (this.role == cp_role.COMMAND) {
                // command server processing method
                if (cpmIn instanceof CPCommandMsg) {
                    // process command
                    commandProcess(cpmIn);
                }else if  (cpmIn instanceof CPCookieVerificationResponseMsg) {
                    handleVerificationResponse((CPCookieVerificationResponseMsg) cpmIn);
                }
            }
            return cpmIn; // return the message
        }
    }

    // Processing of the CookieRequestMsg
    private void cookieProcess(CPMsg cpmIn) throws IWProtocolException, IOException {
        // Obtain client config:
        PhyConfiguration clientConfig = (PhyConfiguration) cpmIn.getConfiguration();
        CPCookieResponseMsg resMsg;

        // if server full, reject request:
        if (cookieMap.size() >= CP_HASHMAP_SIZE) {
            resMsg = new CPCookieResponseMsg(false); // success = false
            resMsg.create("Server full");
        } else {

            /*
             * Improved explanation regarding cookie renewal (as requested in feedback):
             *
             * DECISION: Allow premature cookie renewal by overwriting the old entry.
             * REASONING: According to the specification, any previously issued cookie
             * for a client must be invalidated when a new one is requested. By using
             * cookieMap.put(), the old Cookie object (including its old timestamp) is
             * replaced, effectively invalidating the previous session without requiring
             * complex state checks, keeping the server stateless regarding history.
             */

            // generate new cookie
            int newCookieValue = rnd.nextInt(Integer.MAX_VALUE);
            Cookie cookie = new Cookie(System.currentTimeMillis(), newCookieValue);

            // store new cookie with put() so it overwrites if one already exists for that client.
            cookieMap.put(clientConfig, cookie);

            // return ACK
            resMsg = new CPCookieResponseMsg(true); // success = true
            resMsg.create(Integer.toString(newCookieValue));
        }

        // return the response to the client
        this.PhyProto.send(new String(resMsg.getDataBytes()), clientConfig);
    }

    // Processing of commands received
    private void commandProcess(CPMsg cpmIn) throws IOException, IWProtocolException {
        CPCommandMsg cmd = (CPCommandMsg) cpmIn;
        // add message to the pending command list
        this.pendingCommands.add(cmd);

        // create new verification request for the cookie server
        CPCookieVerificationRequestMsg vReq = new CPCookieVerificationRequestMsg();
        vReq.create(cmd.getCookie());

        // send message to cookie server
        this.PhyProto.send(new String(vReq.getDataBytes()), this.PhyConfigCookieServer);
        System.out.println("VALIDATION SENT: Pending validation for cookie " + cmd.getCookie());
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

        if(count == 3) throw new CookieRequestException();
        if(resMsg instanceof CPCookieResponseMsg && !((CPCookieResponseMsg) resMsg).getSuccess()) {
            throw new CookieRequestException();
        }
         assert resMsg instanceof CPCookieResponseMsg;
         this.cookie = ((CPCookieResponseMsg)resMsg).getCookie();
    }

    // handles cookie verification for cookie-server (input: client command request)
    private void handleVerificationRequest(CPMsg cpmIn) throws IWProtocolException, IOException {
        CPCookieVerificationRequestMsg vReq = (CPCookieVerificationRequestMsg) cpmIn;
        PhyConfiguration senderConfig = (PhyConfiguration) vReq.getConfiguration();

        boolean isValid = false;
        long now = System.currentTimeMillis();
        int cookieToVerify = vReq.getCookieValue();

        // search for the cookie in the cookieMap to see if it has expired (TTL)
        // defined time-to-live: 60s
        final long COOKIE_LIFETIME = 60000;
        for (Cookie c : cookieMap.values()) {
            if (c.getCookieValue() == cookieToVerify) {
                if ((now - c.getTimeOfCreation()) < COOKIE_LIFETIME) {
                    isValid = true;
                }
                break;
            }
        }

        // send response to command server
        CPCookieVerificationResponseMsg vRes = new CPCookieVerificationResponseMsg();
        vRes.create(isValid);
        this.PhyProto.send(new String(vRes.getDataBytes()), senderConfig);
    }

    // handles cookie verification for command-server (input: cookie server response)
    private void handleVerificationResponse(CPCookieVerificationResponseMsg vRes) throws IOException, IWProtocolException {
        if (!pendingCommands.isEmpty()) {
            // obtain the command to check
            CPCommandMsg originalCmd = pendingCommands.removeFirst();

            CPCommandResponseMsg clientRes = new CPCommandResponseMsg();

            if (vRes.isSuccess()) {
                // if success return ok & execute
                System.out.println("EXECUTION: " + originalCmd.getCommand() + " " + originalCmd.getMessage());
                clientRes.create(originalCmd.getId(), true, "Command executed successfully");
            } else {
                // if fail inform client
                clientRes.create(originalCmd.getId(), false, "Invalid or expired cookie");
            }

            // Enviar respuesta final al cliente original
            this.PhyProto.send(new String(clientRes.getDataBytes()), (PhyConfiguration) originalCmd.getConfiguration());
        }
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

