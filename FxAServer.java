import java.io.IOException;
import java.lang.System;
import java.net.InetAddress;
import java.util.Scanner;

public class FxAServer {

    public static void main(String[] args) throws IOException {

        //check arguments
        if (args.length != 3) {
            System.out.println("Invalid number of arguments.");
            System.out.println("Parameters: X A P");
            System.out.println("X is the port number at which FxAServer should bind to (odd number equal to"
                    + " client's port number + 1)");
            System.out.println("A is the IP address of the NetEmu.");
            System.out.println("P is the UDP port number of the NetEmu.");
            System.exit(0);
        }

        System.out.println("Server started. Listening...");

        //save passed in args
        int srcPort = Integer.parseInt(args[0]);
        InetAddress destAddress = InetAddress.getByName(args[1]);
        int destPort = Integer.parseInt(args[2]);

        //create instance of API
        RxPAPI serverRxP = new RxPAPI(destAddress, srcPort, destPort, false);

        //start new thread to accept input at the same time server is listening
        Thread t = new Thread(new ServerListen());
        t.start();
        //start listening
        serverRxP.listen();
    }

    /*
     * Inner class to start a new thread for user input
     */
    private static class ServerListen implements Runnable {

        @Override
        public void run() {
            Scanner sc = new Scanner(System.in);
            while (true) {
                System.out.print(">>");
                String input = sc.nextLine();
                if (input.equals("terminate")) {
                    System.out.println("Exiting the server.");
                    break; //leave loop if user wants to terminate the server
                } else {
                    System.out.println();
                    System.out.println("Please enter a valid command (terminate).");
                    System.out.println("Still listening...");
                }
            }
            //exit the server
            System.exit(0);
        }
    }
}