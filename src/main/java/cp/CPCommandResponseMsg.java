package cp;

import core.Msg;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;

/**
 * Class to parse the server responses
 * format: cp command_response <id> <success> <length> [message] <checksum>
 */
public class CPCommandResponseMsg extends CPMsg {
    // response components
    private int id;
    private boolean success;
    private int length;
    private String responseMessage;
    private long receivedChecksum;
    protected static final String CP_CMD_RES_HEADER = "command_response";


     // Create method (not used but needed because of the inheritance)
    @Override
    protected void create(String sentence) {
        this.data = sentence;
        this.dataBytes = sentence.getBytes();
    }

    /**
     * Parse method:
     * parses the response of the server to users commands
     */
    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        // 'sentence' is the string without the "cp " prefix
        if (!sentence.startsWith(CP_CMD_RES_HEADER)) {
            throw new IllegalMsgException();
            // "Not a command_response message"
        }

        // separate the checksum from the rest of the message
        int checksumSeparatorIndex = sentence.lastIndexOf(' ');
        if (checksumSeparatorIndex == -1) {
            throw new IllegalMsgException();
            // "Invalid format: No checksum found"
        }

        String dataPart = sentence.substring(0, checksumSeparatorIndex);
        String checksumStr = sentence.substring(checksumSeparatorIndex + 1);

        try {
            this.receivedChecksum = Long.parseLong(checksumStr);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
            // "Invalid checksum format"
        }

        // Validate checksum:
        // checksum should be calculated over all message fields except the cp field
        CRC32 crc = new CRC32();
        crc.update(dataPart.getBytes());
        long calculatedChecksum = crc.getValue();

        if (this.receivedChecksum != calculatedChecksum) {
            // corrupt message
            throw new IllegalMsgException();
            // "Checksum mismatch: Message corrupted"
        }

        // Parse the data part
        String[] parts = dataPart.split("\\s+", 5); // max 5 parts
        // Format: command_response <id> <success> <length> [message]
        // parts[0] = "command_response"
        // parts[1] = id
        // parts[2] = success ("ok" o "error")
        // parts[3] = length
        // parts[4] = message (if exists)

        if (parts.length < 4) {
            throw new IllegalMsgException();
            // "Invalid format: Missing required fields"
        }

        try {
            this.id = Integer.parseInt(parts[1]);
            this.success = parts[2].equals("ok");
            this.length = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException();
            // "Invalid format for id or length"
        }

        // Extract the message
        this.responseMessage = "";
        if (this.length > 0 && parts.length == 5) {
            this.responseMessage = parts[4]; // [cite: 508]

            // verify length
            if (this.responseMessage.length() != this.length) {
                // if statement to correct spaces before and after the command supposed to be corrected by split()
                throw new IllegalMsgException();
            }

        } else if (this.length > 0 && parts.length < 5) {
            throw new IllegalMsgException();
            // "Invalid format: Length > 0 but no message found"
        }

        return this;
    }
    // Getters (needed for receive() in CPProtocol)
    public int getId() {
        return this.id;
    }
    public boolean isSuccess() {
        return this.success;
    }
    public String getResponseMessage() {
        return this.responseMessage;
    }

}
