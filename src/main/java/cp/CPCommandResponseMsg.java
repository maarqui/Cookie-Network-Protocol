package cp;

import core.Msg;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

     /* RegEx structure:
     * ^command_response\\s+     : [Header]: starts with command_response and one (or more) spaces .
     * (\\d{1,5})\\s+            : [ID]: captures 1 to 5 digits (max 65535) followed by spaces.
     * (ok|error)\\s+            : [Success]: captures "ok" or "error", followed by spaces.
     * (\\d+)\\s*                : [Length]: captures one or more digits (length), followed by 0 or spaces.
     * (.*)$                     : [Message]: captures all characters (including spaces) until the end of the line.
     */
    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "^" + CP_CMD_RES_HEADER + "\\s+(\\d{1,5})\\s+(ok|error)\\s+(\\d+)\\s*(.*)$"
    );


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
        // Separate checksum:
        int checksumSeparatorIndex = sentence.lastIndexOf(' ');
        if (checksumSeparatorIndex == -1) {
            throw new IllegalMsgException("Invalid format: No checksum found");
        }

        String dataPart = sentence.substring(0, checksumSeparatorIndex);
        String checksumStr = sentence.substring(checksumSeparatorIndex + 1);

        try {
            this.receivedChecksum = Long.parseLong(checksumStr);
        } catch (NumberFormatException e) {
            throw new IllegalMsgException("Invalid checksum format");
        }

        // Validate checksum:
        // checksum should be calculated over all message fields except the cp field
        CRC32 crc = new CRC32();
        crc.update(dataPart.getBytes());
        long calculatedChecksum = crc.getValue();

        if (this.receivedChecksum != calculatedChecksum) {
            // corrupt message
            throw new IllegalMsgException("Checksum mismatch: Message corrupted");
        }

        // Parse the data part (using RegEx):
        Matcher matcher = RESPONSE_PATTERN.matcher(dataPart); // Matcher for the RegEx.

        // if 'dataPart' string doesn't fit in the pattern, reject message
        if (!matcher.find()) {
            throw new IllegalMsgException("Invalid command_response format");
        }

        // if 'dataPart' has the correct pattern, extract the fields.
        try {
            // ID
            this.id = Integer.parseInt(matcher.group(1));
            // Success
            this.success = matcher.group(2).equals("ok");
            // Length
            this.length = Integer.parseInt(matcher.group(3));
            // Message
            this.responseMessage = matcher.group(4);
        } catch (NumberFormatException e) {
            // checks if ID & length are numbers as expected
            throw new IllegalMsgException("Invalid number format in message (RegEx failed)");
        }

        // Check if the lengths match:
        if (this.responseMessage.length() != this.length) {
            throw new IllegalMsgException("Message length mismatch (Expected: " +
                    this.length + ", Got: " + this.responseMessage.length() + ")");
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
