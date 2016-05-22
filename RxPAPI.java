import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RxPAPI {

    private final int ECHOMAX = 255;

    private static final int TIMEOUT = 500;
    private static final int MAXTRIES = 50;

    private DatagramSocket socket;
    private InetAddress destAddress;

    DatagramPacket recvPkt;
    DatagramPacket sendPkt;

    private int srcPort;
    private int destPort;

    private int sequenceNum;

    private String fileName;

    private String challenge;

    private List<RxPPacket> pktSendBuff;
    private List<RxPPacket> pktRecvBuff;

    private boolean client;

    private int windowSize = 1;

    //following are for sliding window only
    private int currentWindowSize;
    private int currentSeqNumStart;
    private boolean[] recvdPktArr;
    private final Object lock = new Object();

    /* State
     * false = CLOSED
     * true = ESTABLISHED
     */
    private boolean connected = false;


    public RxPAPI(InetAddress destAddress, int srcPort, int destPort, boolean client) {
        this.destAddress = destAddress;
        this.srcPort = srcPort;
        this.destPort = destPort;
        this.client = client;

        //Initialize buffers
        pktSendBuff = new ArrayList<>();
        pktRecvBuff = new ArrayList<>();

        try {
            socket = new DatagramSocket(srcPort);
            if (client) {
                socket.setSoTimeout(TIMEOUT);
            }
        } catch (SocketException e) {
            System.out.println("Could not create a socket.");
        }
    }

    /**
     * Method for client, called when trying to connect to server.
     * @throws java.io.IOException
     */
    public void createConnection() throws IOException {
        if (connected) {
            System.out.println("Connection is already established.");
        } else {
            //Send connection request with a SYN packet
            RxPHeader synHeader = new RxPHeader(srcPort, destPort, 0);
            synHeader.setSYN(true);

            RxPPacket synPkt = new RxPPacket(synHeader, null);
            synPkt.updateChecksum();

            byte[] synPktBytes = synPkt.getPacketByteArray();
            sendPkt = new DatagramPacket(synPktBytes, synPktBytes.length, destAddress, destPort);

            //keep trying to connect up to MAXTRIES
            while (true) {
                int tries = 0;
                boolean recvdResponse = false;
                //keep sending until a message is received
                do {
                    socket.send(sendPkt);
                    try {
                        recvPkt = new DatagramPacket(new byte[ECHOMAX], ECHOMAX);
                        socket.receive(recvPkt);
                        recvdResponse = true;
                    } catch (InterruptedIOException e) {
                        tries++;
                    }
                } while ((!recvdResponse) && (tries < MAXTRIES));

                if (recvdResponse) {
                    //read received packet
                    byte[] recvd = new byte[recvPkt.getLength()];

                    recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

                    RxPPacket recvdRxPPkt = new RxPPacket(recvd);
                    RxPHeader recvdHeader = recvdRxPPkt.getHeader();

                    //check for data corruption
                    if (recvdHeader.getChecksum() == recvdRxPPkt.calculateChecksum()) {
                        //check which message was received; if it's not an ACK,
                        //then it must be a challenge that was sent
                        if (recvdHeader.isACK()) {
                            connected = true;
                            System.out.println("Connection established.");
                            break;
                        } else {
                            byte[] chalData = recvdRxPPkt.getData();
                            byte[] hashedChal = md5(chalData);

                            RxPHeader hashChalHeader = new RxPHeader(srcPort, destPort, 0);
                            hashChalHeader.setSYN(true);
                            RxPPacket hashChalPkt = new RxPPacket(hashChalHeader, hashedChal);
                            hashChalPkt.updateChecksum();

                            byte[] hashChalPktBytes = hashChalPkt.getPacketByteArray();
                            sendPkt = new DatagramPacket(hashChalPktBytes, hashChalPktBytes.length, destAddress, destPort);
                        }
                    }
                } else {
                    //if connection was not made
                    System.out.println("Could not connect. Please try again.");
                    break;
                }
            }
        }
    }

    /**
     * Method for server, for listening.
     * @throws java.io.IOException
     */
    public void listen() throws IOException {
        while (true) {
            //receive a client request
            recvPkt = new DatagramPacket(new byte[ECHOMAX+28], ECHOMAX+28);
            socket.receive(recvPkt);

            byte[] recvd = new byte[recvPkt.getLength()];

            recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

            RxPPacket recvdRxPPkt = new RxPPacket(recvd);
            RxPHeader recvdHeader = recvdRxPPkt.getHeader();

            //check for corruptions
            if (recvdHeader.getChecksum() == recvdRxPPkt.calculateChecksum()) {
                //check if it the client is trying to connect
                if (recvdHeader.isSYN()) {
                    if (recvdRxPPkt.getData() == null) {
                        challenge = generateString(new Random(), 64);
                        byte[] chalBytes = challenge.getBytes();

                        RxPHeader chalHeader = new RxPHeader(srcPort, destPort, 0);
                        RxPPacket chalPkt = new RxPPacket(chalHeader, chalBytes);
                        chalPkt.updateChecksum();

                        byte[] chalPktBytes = chalPkt.getPacketByteArray();
                        sendPkt = new DatagramPacket(chalPktBytes, chalPktBytes.length, destAddress, destPort);

                        socket.send(sendPkt);
                    } else {
                        RxPHeader ackHeader = new RxPHeader(srcPort, destPort, 0);
                        ackHeader.setACK(true);

                        RxPPacket ackPkt = new RxPPacket(ackHeader, null);
                        ackPkt.updateChecksum();

                        byte[] ackPktBytes = ackPkt.getPacketByteArray();
                        sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);

                        socket.send(sendPkt);
                    }
                //otherwise, client is sending another request such as get or change window
                } else {
                    handleClient(recvdRxPPkt);
                }
            }
        }
    }

    /**
     * Helper method that handles a client's request.
     * @param pkt the request packer received from the client.
     * @throws java.io.IOException
     */
    public void handleClient(RxPPacket pkt) throws IOException {
        byte[] data = pkt.getData();
        if (data != null) {
            String datastr = new String(data);
            String[] dataArr = datastr.split(":");
            //if request is to change the window size
            if (dataArr[0].equals("window")) {
                windowSize = Integer.parseInt(dataArr[1]);

                RxPHeader ackHeader = new RxPHeader(srcPort, destPort, 0);
                ackHeader.setACK(true);

                RxPPacket ackPkt = new RxPPacket(ackHeader, null);
                ackPkt.updateChecksum();

                byte[] ackPktBytes = ackPkt.getPacketByteArray();
                sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);

                socket.send(sendPkt);
            }
            //if request is to download a file
            if (dataArr[0].equals("get")) {
                fileName = dataArr[1];
                if (windowSize == 1) {
                    //send file directly if stop-and-wait
                    sendFile(dataArr[1]);
                } else {
                    //start thread if window greater than 1
                    Thread t = new Thread(new ServerSend());
                    t.start();

                    while (t.isAlive()) {
                        //keep receiving ACK packets while thread is sending file
                        recvPkt = new DatagramPacket(new byte[ECHOMAX + 28], ECHOMAX + 28);
                        socket.receive(recvPkt);

                        byte[] recvd = new byte[recvPkt.getLength()];

                        recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

                        RxPPacket recvdRxPPkt = new RxPPacket(recvd);
                        RxPHeader recvdHeader = recvdRxPPkt.getHeader();
                        int sqNum = recvdHeader.getSequenceNumber();
                        synchronized (lock) {
                            if ((currentSeqNumStart <= sqNum) && (sqNum < currentSeqNumStart + currentWindowSize)) {
                                //fill array for which packets in current window were received so the server knows
                                //see inner class ServerSend below for when server checks this
                                recvdPktArr[sqNum % windowSize] = true;
                            }
                        }
                    }
                }
                //return sequence number back to 0 after request is complete
                sequenceNum = 0;
            }
        }
    }

    /**
     * Method for client, called when trying to update the window size.
     * @param win the window size as a String.
     * @throws java.io.IOException
     */
    public void updateWindow(String win) throws IOException {
        if (!connected) {
            System.out.println("No connection established. Try to connect first.");
        } else {
            try {
                Integer.parseInt(win);
                RxPHeader winHeader = new RxPHeader(srcPort, destPort, 0);

                String winstr = "window:" + win;
                RxPPacket winPkt = new RxPPacket(winHeader, winstr.getBytes());
                winPkt.updateChecksum();

                byte[] winPktBytes = winPkt.getPacketByteArray();
                sendPkt = new DatagramPacket(winPktBytes, winPktBytes.length, destAddress, destPort);
                boolean recvdResponse = false;
                //keep sending window size to server until it receives it
                do {
                    socket.send(sendPkt);
                    try {
                        recvPkt = new DatagramPacket(new byte[ECHOMAX + 28], ECHOMAX + 28);
                        socket.receive(recvPkt);
                        recvdResponse = true;
                    } catch (InterruptedIOException e) {
                        //tries sending packet again
                    }
                } while (!recvdResponse);

                windowSize = Integer.parseInt(win);
                System.out.println("Window size changed to " + win + ".");
            } catch (NumberFormatException e) {
                //check if valid integer was passed in
                System.out.println("Please enter an integer as the window size.");
            }
        }
    }

    /**
     * Method for client, called when trying to download a file from the server.
     * @param filename the name of the file to download.
     * @throws java.io.IOException
     */
    public void recvFrom(String filename) throws IOException {
        if (!connected) {
            System.out.println("No connection established. Try to connect first.");
        } else {
            File f = new File(filename);
            if (f.exists()) {
                fileName = filename;
                RxPHeader getHeader = new RxPHeader(srcPort, destPort, 0);

                String getstr = "get:" + filename;
                RxPPacket getPkt = new RxPPacket(getHeader, getstr.getBytes());
                getPkt.updateChecksum();

                byte[] getPktBytes = getPkt.getPacketByteArray();
                sendPkt = new DatagramPacket(getPktBytes, getPktBytes.length, destAddress, destPort);

                System.out.println("Attempting file retrieval...");
                boolean endOfFile = false;
                //keep receiving until final packet is received
                while (!endOfFile) {
                    boolean recvdResponse = false;
                    //keep sending until a message is received
                    do {
                        socket.send(sendPkt);
                        try {
                            recvPkt = new DatagramPacket(new byte[ECHOMAX + 28], ECHOMAX + 28);
                            socket.receive(recvPkt);
                            recvdResponse = true;
                        } catch (InterruptedIOException e) {
                            //tries sending packet again
                        }
                    } while (!recvdResponse);

                    //read received message
                    byte[] recvd = new byte[recvPkt.getLength()];

                    recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

                    RxPPacket recvdRxPPkt = new RxPPacket(recvd);
                    RxPHeader recvdHeader = recvdRxPPkt.getHeader();

                    //check message for corruption
                    if (recvdHeader.getChecksum() == recvdRxPPkt.calculateChecksum()) {
                        //check sequence number
                        if (recvdHeader.getSequenceNumber() == sequenceNum) {
                            //check if final packet
                            if (recvdHeader.isFIN()) {
                                endOfFile = true;
                            }
                            pktRecvBuff.add(recvdRxPPkt);

                            RxPHeader ackHeader = new RxPHeader(srcPort, destPort, 0);
                            ackHeader.setACK(true);
                            ackHeader.setSequenceNumber(sequenceNum);

                            RxPPacket ackPkt = new RxPPacket(ackHeader, null);
                            ackPkt.updateChecksum();

                            byte[] ackPktBytes = ackPkt.getPacketByteArray();
                            sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);

                            //increment sequence number for receiving next packet
                            sequenceNum++;
                        }
                    }
                }

                //send final ACK packet
                socket.send(sendPkt);

                //place received file in stream
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (RxPPacket pkt : pktRecvBuff) {
                    if (pkt.getData() != null) {
                        baos.write(pkt.getData());
                    }
                }

                //create received file
                try {
                    String[] newfile = fileName.split("\\.");
                    FileOutputStream fileOutputStream = new FileOutputStream(newfile[0] + "1." + newfile[1]);
                    fileOutputStream.write(baos.toByteArray());
                    fileOutputStream.close();
                    System.out.println("File was downloaded successfully.");
                } catch (java.io.FileNotFoundException e) {
                    System.out.println("File was not found.");
                } catch (IOException e) {
                    System.out.println("Error writing file.");
                }
                pktRecvBuff.clear();
                sequenceNum = 0;
            } else {
                System.out.println("The file does not exist.");
            }
        }
    }

    /**
     * Helper method for server to use when sending a file requested by the client
     * @param filename the name of the file to send
     * @throws java.io.IOException
     */
    public void sendFile(String filename) throws IOException {
        File file = new File(filename);
        byte[] fileArray = new byte[(int)file.length()];

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(fileArray);

            byte[] filePacket;
            if (fileArray.length < ECHOMAX) {
                filePacket = new byte[fileArray.length];
            } else {
                filePacket = new byte[ECHOMAX];
            }
            int count = fileArray.length;
            int pos = 0;
            //check if entire file has been sent or not
            while (count > 0) {
                //check whether there are at least 255 bytes left to send
                if (count/255 >= 1) {
                    for (int i = 0; i < 255; i++) {
                        filePacket[i] = fileArray[pos];
                        pos++;
                    }

                    RxPHeader header = new RxPHeader(srcPort, destPort, sequenceNum);
                    if (count == 255) {
                        header.setFIN(true);
                    }
                    RxPPacket filePkt = new RxPPacket(header, filePacket);
                    filePkt.updateChecksum();
                    byte[] ackPktBytes = filePkt.getPacketByteArray();
                    sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);
                    socket.send(sendPkt);

                    recvPkt = new DatagramPacket(new byte[ECHOMAX + 28], ECHOMAX + 28);
                    socket.receive(recvPkt);

                    byte[] recvd = new byte[recvPkt.getLength()];

                    recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

                    RxPPacket recvdRxPPkt = new RxPPacket(recvd);
                    RxPHeader recvdHeader = recvdRxPPkt.getHeader();
                    if (recvdHeader.isACK() && recvdHeader.getSequenceNumber() == sequenceNum) {
                        //updates if sequenceNumber of ACK packet is correct
                        sequenceNum++;
                        count = count - 255;
                    } else {
                        //otherwise try sending same packet again
                        pos -= 255;
                    }
                //final packet may be less than 255 bytes
                } else {
                    int src = (fileArray.length/255) * 255;
                    int dest = fileArray.length - src;
                    filePacket = new byte[dest];
                    System.arraycopy(fileArray,src,filePacket, 0, dest);
                    RxPHeader header = new RxPHeader(srcPort, destPort, sequenceNum);
                    //final packet's FIN bit is true
                    header.setFIN(true);
                    RxPPacket filePkt = new RxPPacket(header, filePacket);
                    filePkt.updateChecksum();
                    byte[] ackPktBytes = filePkt.getPacketByteArray();
                    sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);
                    socket.send(sendPkt);

                    recvPkt = new DatagramPacket(new byte[ECHOMAX+28], ECHOMAX+28);
                    socket.receive(recvPkt);

                    byte[] recvd = new byte[recvPkt.getLength()];

                    recvd = Arrays.copyOfRange(recvPkt.getData(), 0, recvPkt.getLength());

                    RxPPacket recvdRxPPkt = new RxPPacket(recvd);
                    RxPHeader recvdHeader = recvdRxPPkt.getHeader();
                    if (!recvdHeader.isACK() || recvdHeader.getSequenceNumber() == sequenceNum) {
                        //updates if sequenceNumber of last ACK packet is correct
                        count = 0;
                        sequenceNum++;
                    }
                }
            }
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found");
        } catch (IOException e) {
            System.out.println("File error.");
        }

    }

    /**
     * Method for client, called when trying to disconnect from the server.
     */
    public void close() {
        if (!connected) {
            System.out.println("You haven't connected yet.");
        } else {
            //close connection
            connected = false;
            System.out.println("Disconnected from server.");
        }
    }

    /**
     * Helper method to generate random string for establishing a connection.
     * @param rng
     * @param length the length of the string
     */
    public String generateString(Random rng, int length) {
        char[] text = new char[length];
        String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";//,.-#'?!";
        for (int i = 0; i < length; i++) {
            text[i] = characters.charAt(rng.nextInt(characters.length()));
        }
        return new String(text);
    }

    /**
     * Helper method to generate an MD5 hash for establishing a connection.
     * @param bytesToDigest bytes that need to be hashed.
     */
    public byte[] md5(byte[] bytesToDigest) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(bytesToDigest);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Inner class to run a separate thread for sliding window protocol.
     */
    private class ServerSend implements Runnable {

        @Override
        public void run() {
            File file = new File(fileName);
            byte[] fileArray = new byte[(int)file.length()];

            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                fileInputStream.read(fileArray);

                byte[] filePacket;
                if (fileArray.length < ECHOMAX) {
                    filePacket = new byte[fileArray.length];
                } else {
                    filePacket = new byte[ECHOMAX];
                }
                int count = fileArray.length;
                int pos = 0;
                int pktsSent = 0;
                List<RxPPacket> storedPkts = new ArrayList<>();
                synchronized (lock) {
                    currentWindowSize = Math.min(fileArray.length / 255 + 1, windowSize);
                    recvdPktArr = new boolean[currentWindowSize];
                }
                //send until entire file is sent
                while (count > 0) {
                    //send packets in current window
                    while (pktsSent < currentWindowSize) {
                        //check if there are at least 255 bytes left
                        if (count / 255 >= 1) {
                            for (int i = 0; i < 255; i++) {
                                filePacket[i] = fileArray[pos];
                                pos++;
                            }

                            RxPHeader header = new RxPHeader(srcPort, destPort, sequenceNum);
                            //last packet
                            if (count == 255) {
                                header.setFIN(true);
                            }
                            RxPPacket filePkt = new RxPPacket(header, filePacket);
                            filePkt.updateChecksum();
                            storedPkts.add(filePkt);
                            byte[] ackPktBytes = filePkt.getPacketByteArray();
                            sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);
                            socket.send(sendPkt);

                            sequenceNum++;
                            count = count - 255;

                        //otherwise it is the last packet with less than 255 bytes
                        } else {
                            int src = (fileArray.length / 255) * 255;
                            int dest = fileArray.length - src;
                            filePacket = new byte[dest];
                            System.arraycopy(fileArray, src, filePacket, 0, dest);
                            RxPHeader header = new RxPHeader(srcPort, destPort, sequenceNum);
                            //last packet
                            header.setFIN(true);
                            RxPPacket filePkt = new RxPPacket(header, filePacket);
                            filePkt.updateChecksum();
                            storedPkts.add(filePkt);
                            byte[] ackPktBytes = filePkt.getPacketByteArray();
                            sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);
                            socket.send(sendPkt);

                            count = 0;
                            sequenceNum++;
                        }
                        pktsSent++;
                    }

                    boolean allRecvd = false;
                    //check if all packets in current window have been received
                    while (!allRecvd) {
                        //wait for client to receive
                        try {
                            Thread.sleep(400);
                        } catch (InterruptedException e) {
                            //...
                        }

                        synchronized (lock) {
                            allRecvd = true;
                            //check if all packets are received
                            for (int i = 0; i < recvdPktArr.length; i++) {
                                if (!recvdPktArr[i]) {
                                    allRecvd = false;
                                    RxPPacket pkt = storedPkts.get(i);
                                    byte[] ackPktBytes = pkt.getPacketByteArray();
                                    sendPkt = new DatagramPacket(ackPktBytes, ackPktBytes.length, destAddress, destPort);
                                    socket.send(sendPkt);
                                }
                            }
                        }
                    }
                    pktsSent = 0;
                    synchronized (lock) {
                        //update all variables
                        currentSeqNumStart += currentWindowSize;
                        currentWindowSize = Math.min(count / 255 + 1, windowSize);
                        storedPkts.clear();
                        recvdPktArr = new boolean[currentWindowSize];
                    }
                }
                fileInputStream.close();
            } catch (FileNotFoundException e) {
                System.out.println("File Not Found");
            } catch (IOException e) {
                System.out.println("File error.");
            }
        }
    }
}