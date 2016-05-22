import java.io.IOException;
import java.net.InetAddress;
import java.util.Scanner;

public class FxAClient {

    public static void main(String[] args) throws IOException {

        //check arguments
        if (args.length != 3) {
            System.out.println("Invalid number of arguments.");
            System.out.println("Parameters: X A P");
            System.out.println("X is the port number at which FxAClient should bind to (even number equal to"
                    + " server's port number minus 1)");
            System.out.println("A is the IP address of the NetEmu.");
            System.out.println("P is the UDP port number of the NetEmu.");
            System.exit(0);
        }

        System.out.println("Client started.");

        //save passed in args
        int srcPort = Integer.parseInt(args[0]);
        InetAddress destAddress = InetAddress.getByName(args[1]);
        int destPort = Integer.parseInt(args[2]);

        //create instance of API
        RxPAPI clientRxP = new RxPAPI(destAddress, srcPort, destPort, true);

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("Please enter one of the following commands:");
            System.out.println("connect, get F, post F, window W, disconnect");
            System.out.print(">>");

            String command = sc.nextLine();
            System.out.println();

            String[] commands = command.split(" ");

            if (command.equals("connect")) {
                clientRxP.createConnection();
            } else if (commands[0].equals("get")) {
                clientRxP.recvFrom(commands[1]);
            } else if (commands[0].equals("window")) {
                clientRxP.updateWindow(commands[1]);
            } else if (command.equals("disconnect")) {
                clientRxP.close();
            } else {
                System.out.println("Please enter a valid command with correct spelling and spacing.");
            }
        }
    }
}