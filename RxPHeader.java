import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

public class RxPHeader {
    private int sourcePort;
    private int destinationPort;
    private int sequenceNumber;
    private int ackNumber;
    private int checksum;

    private boolean ACK;
    private boolean SYN;
    private boolean FIN;

    private int timestamp;

    public RxPHeader(int sourcePort, int destinationPort, int sequenceNumber) {

        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sequenceNumber = sequenceNumber;

        this.checksum = 0;
        this.SYN = false;
        this.FIN = false;
        this.ACK = false;

        this.timestamp = 0;
    }

    //getters and setters
    public int getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public boolean isACK() {
        return ACK;
    }

    public void setACK(boolean ACK) {
        this.ACK = ACK;
    }

    public boolean isSYN() {
        return SYN;
    }

    public void setSYN(boolean SYN) {
        this.SYN = SYN;
    }

    public boolean isFIN() {
        return FIN;
    }

    public void setFIN(boolean FIN) {
        this.FIN = FIN;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }


    public byte[] getHeaderByteArray() {
        //Initializes the byte array to return
        byte[] headerByteArray;

        //Allocate space to store header bytes
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES * 7);
        //byteBuffer.order(ByteOrder.BIG_ENDIAN); //not sure if we need this

        //Place header info in buffer
        byteBuffer.putInt(sourcePort);
        byteBuffer.putInt(destinationPort);
        byteBuffer.putInt(sequenceNumber);
        byteBuffer.putInt(ackNumber);
        byteBuffer.putInt(checksum);

        //Converts the flags to ints with bitshifting
        int ackByte = (ACK ? 1 : 0) << 31;
        int synByte = (SYN ? 1 : 0) << 30;
        int finByte = (FIN ? 1 : 0) << 29;

        //combine flags
        int flagsCombined = ackByte | synByte | finByte;

        //Place flags byte array in buffer
        byteBuffer.putInt(flagsCombined);

        //Place timestamp in buffer
        byteBuffer.putInt(timestamp);
        //Convert the byte buffer into a byte array.
        headerByteArray = byteBuffer.array();

        //Return the completed byte buffer.
        return headerByteArray;
    }

    /*
     * Constructor for an RxP Header from a byte array.
     */
    public RxPHeader(byte[] headerByteArray) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(headerByteArray);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        this.sourcePort = intBuffer.get(0);
        this.destinationPort = intBuffer.get(1);
        this.sequenceNumber = intBuffer.get(2);
        this.ackNumber = intBuffer.get(3);
        this.checksum = intBuffer.get(4);
        int flagsCombined = intBuffer.get(5);
        this.timestamp = intBuffer.get(6);


        int ackInt = (flagsCombined >>> 31) & 0x1;
        int synInt = (flagsCombined >>> 30) & 0x1;
        int finInt = (flagsCombined >>> 29) & 0x1;

        this.ACK = (ackInt == 1);
        this.SYN = (synInt == 1);
        this.FIN = (finInt == 1);

    }


    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}