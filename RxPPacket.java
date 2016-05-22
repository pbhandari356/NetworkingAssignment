import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


public class RxPPacket {

    // Header for the RxP packet
    private RxPHeader header;

    // Packet data/payload
    private byte[] data;


    /*
     * Constructor for a RxP packet with a provided header and data
     */
    public RxPPacket(RxPHeader header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    /*
     *  Constructor for a RxP packet from a byte array.
     */
    public RxPPacket(byte[] packetByteArray) {
        // Obtains bytes in header
        byte[] headerBytes = Arrays.copyOfRange(packetByteArray, 0, 28);
        this.setHeader(new RxPHeader(headerBytes));

        // Obtains bytes in data
        if (packetByteArray.length > 28) {
            byte[] dataBytes = Arrays.copyOfRange(packetByteArray, 28, packetByteArray.length);
            this.setData(dataBytes);
        }
    }

    /*
     * Convert a packet into a byte array
     */
    public byte[] getPacketByteArray() {
        byte[] packetByteArray;
        byte[] headerByteArray;

        headerByteArray = header.getHeaderByteArray();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(headerByteArray);
            if (data != null) {
                outputStream.write(data);
            }
        } catch (IOException e) {
            //catch exception
        }

        packetByteArray = outputStream.toByteArray();

        return packetByteArray;
    }

    //getters and setters
    public RxPHeader getHeader() {
        return header;
    }

    public void setHeader(RxPHeader header) {
        this.header = header;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    /*
     * Updates the checksum field in the packet header
     */
    public void updateChecksum() {
        header.setChecksum(calculateChecksum());
    }

    /*
     * Calculate checksum using CRC library
     */
    public int calculateChecksum() {
        int checksumValue;

        CRC32 checksum = new CRC32();

        byte[] packetByteArray = getPacketByteArray();

        // Don't include the actual checksum field when calculating
        packetByteArray[16] = 0x00;
        packetByteArray[17] = 0x00;
        packetByteArray[18] = 0x00;
        packetByteArray[19] = 0x00;

        checksum.update(packetByteArray);
        checksumValue = (int) checksum.getValue();

        return checksumValue;
    }

}