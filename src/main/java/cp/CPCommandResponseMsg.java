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
     * ^command_response\\s+     : [Header]: starts with command_response and one (or more) spaces.
     * (\\d{1,5})\\s+            : [ID]: captures 1 to 5 digits (max 65535) followed by spaces.
     * (ok|error)\\s+            : [Success]: captures "ok" or "error", followed by spaces.
     * (\\d+)                    : [Length]: captures one or more digits (length).
     * (?:\\s+(.*))?             : [Message]: optionally captures a space followed by characters until the end.
     * \s*$                      : [End]: allows for optional trailing spaces before the end
     */
    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "^" + CP_CMD_RES_HEADER + "\\s+(\\d{1,5})\\s+(ok|error)\\s+(\\d+)(?:\\s+(.*?))?\\s*$"
    );

    @Override
    protected void create(String sentence) {
        this.data = sentence;
        this.dataBytes = sentence.getBytes();
    }

    /**
     * Create method:
     * allows the creation of response messages from the server
     */
    public void create(int id, boolean success, String responseText) {
        this.id = id;
        this.success = success;
        this.responseMessage = responseText;
        this.length = responseText.length();
        String status = success ? "ok" : "error";

        // format: cp command_response <id> <success> <length> [message]
        String dataPart = String.format("%s %d %s %d %s",
                CP_CMD_RES_HEADER, this.id, status, this.length, this.responseMessage).trim();

        // calculate checksum
        CRC32 crc = new CRC32();
        crc.update(dataPart.getBytes());
        long checksum = crc.getValue();

        // add checksum to response message
        String finalMsg = dataPart + " " + checksum;

        this.create(finalMsg);
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
        if (checksumSeparatorIndex == -1) throw new IllegalMsgException("Invalid format: No checksum found");

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

        if (this.receivedChecksum != crc.getValue()) {
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
            // ID
            this.id = Integer.parseInt(matcher.group(1));
            // Success
            this.success = matcher.group(2).equals("ok");
            // Length
            this.length = Integer.parseInt(matcher.group(3));
            // Message (optional group)
            String msgGroup = matcher.group(4);
            this.responseMessage = (msgGroup == null) ? "" : msgGroup;

        // Check if the lengths match:
        if (this.responseMessage.length() != this.length) {
            throw new IllegalMsgException("Message length mismatch (Expected: " +
                    this.length + ", Got: " + this.responseMessage.length() + ")");
        }

        return this;
    }

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
