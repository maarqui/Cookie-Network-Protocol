package cp;

import core.Msg;
import exceptions.IllegalMsgException;
import java.util.zip.CRC32;

// Class that stores the contents of the commands sent by the user
public class CPCommandMsg extends CPMsg{

    protected static final String CP_CMD_HEADER =  "command";

    // message fields
    private int id;
    private int cookie;
    private int length;
    private String command;
    private String message;
    private long checksum;

    // empty constructor
    public CPCommandMsg() {}

    @Override
    protected void create(String sentence) {
        // simple create version to inherit from Msg
        this.data = sentence;
        this.dataBytes = sentence.getBytes();
    }

    /**
     * Create command method: constructor to assemble a complete command message
     * @param s raw command from user
     * @param id exclusive id of the command
     * @param cookie cookie obtained by the server
     */
    public void create(String s, int id, int cookie) {
        this.id = id;
        this.cookie = cookie;
        this.message = ""; // by default the command message is empty

        // analyze the contents of the command ("s")
        if (s.startsWith("print")) {
            this.command = "print";
            // extract the text to print (assuming the text to print begins on the 6th char)
            if (s.length() > 6) {
                this.message = s.substring(6);
            }
        } else if (s.equals("status")) {
            this.command = "status";
            // for the status command the field message is not necessary
        } else {
            // unknown command filter
            this.command = s;
        }

        // Calculate length:
        // command field + optional message field
        this.length = this.command.length();
        if (!this.message.isEmpty()) {
            this.length += this.message.length();
        }

        // Assembling the message for the checksum:
        // checksum is calculated over all message fields except the cp field
        String checksumData;
        if (this.message.isEmpty()) {
            // 'empty message' format
            checksumData = String.format("%s %d %d %d %s",
                    CP_CMD_HEADER, this.id, this.cookie, this.length, this.command);
        } else {
            // 'complete command' message
            checksumData = String.format("%s %d %d %d %s %s",
                    CP_CMD_HEADER, this.id, this.cookie, this.length, this.command, this.message);
        }

        // Calculate the checksum using CRC32: (https://docs.oracle.com/javase/9/docs/api/java/util/zip/CRC32.html)
        CRC32 crc = new CRC32();
        crc.update(checksumData.getBytes()); // generates the checksum using the bytes of the message previously assembled
        this.checksum = crc.getValue();

        // Assemble the final string (command data + checksum)
        String finalMsg = String.format("%s %d", checksumData, this.checksum);

        // Call super.create()
        super.create(finalMsg);
    }

    @Override
    protected Msg parse(String sentence) throws IllegalMsgException {
        // separate checksum, if not found throw exception
        int checksumSeparatorIndex = sentence.lastIndexOf(' ');
        if (checksumSeparatorIndex == -1) throw new IllegalMsgException("No checksum");

        String dataPart = sentence.substring(0, checksumSeparatorIndex);
        String checksumStr = sentence.substring(checksumSeparatorIndex + 1);

        // validate checksum
        CRC32 crc = new CRC32();
        crc.update(dataPart.getBytes());
        if (Long.parseLong(checksumStr) != crc.getValue()) {
            throw new IllegalMsgException("Checksum error");
        }

        // format: command (id) (cookie) (length) (command) [message] (checksum)
        String[] parts = dataPart.split("\\s+", 6);
        // 5 parts minimum (checksum separated)
        if (parts.length < 5) throw new IllegalMsgException("Invalid command format");

        this.id = Integer.parseInt(parts[1]);
        this.cookie = Integer.parseInt(parts[2]);
        this.length = Integer.parseInt(parts[3]);
        this.command = parts[4];
        this.message = (parts.length == 6) ? parts[5] : "";

        return this;
    }

    public int getId() {
        return this.id;
    }
    public int getCookie() { return this.cookie; }
    public String getCommand() { return this.command; }
    public String getMessage() { return this.message; }


}
