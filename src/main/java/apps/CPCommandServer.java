package apps;

import core.Msg;
import cp.CPProtocol;
import exceptions.IWProtocolException;
import phy.PhyProtocol;

import java.io.IOException;
import java.net.InetAddress;

public class CPCommandServer {
    protected static final int COMMAND_SERVER_PORT = 2000;

    public static void main(String[] args) {
        // configure physical layer (UDP)
        PhyProtocol phy = new PhyProtocol(COMMAND_SERVER_PORT);

        // configure command protocol in COMMAND role
        CPProtocol cp;
        try {
            cp = new CPProtocol(phy, false);

            cp.setCookieServer(InetAddress.getByName("localhost"), CPCookieServer.COOKIE_SERVER_PORT);
            System.out.println("--- Command Server Started on Port " + COMMAND_SERVER_PORT + " ---");
            System.out.println("--- Linked to Cookie Server on Port " + CPCookieServer.COOKIE_SERVER_PORT + " ---");
        } catch (Exception e) {
            System.err.println("Failed to initialize CPProtocol");
            return;
        }

        // processing loop
        while (true) {
            try {
                // receive() uses commandProcess() to process the commands received from users
                cp.receive();
            } catch (IOException e) {
                System.out.println("IO Error during command processing: " + e.getMessage());
            } catch (IWProtocolException e) {
                System.out.println("Discarded a malformed message.");
            }
        }
    }

}
