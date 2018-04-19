import java.io.PrintStream;
import java.lang.Thread;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.function.Function;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Scanner;

/**
 * This class instantiates the whole Ringo application. Given the correct number
 * and type of command-line arguments, this class starts a Ringo node as a child
 * Thread
 * 
 * @author sainaidu
 */
public class App {
	public static final int NUM_ARGS = 5;
	public static final int MAX_PORT = 65535;

	private static Role role;
	private static int port;
	private static String pocHost;
	private static int pocPort;
	private static int n;
	private static LinkedBlockingQueue<String> userCommandList;
	
	private static DatagramSocket socket;

	public static void main(String[] args) {
		userCommandList = new LinkedBlockingQueue<String>();
		
		try {
			parseArguments(args);
		} catch (IllegalArgumentException e) {
			printHelp(System.err);
			System.out.println(e.getMessage());
			System.exit(1);
		}

		System.out.println(String.format("Provided Arguments: Role: %s\tLocal Port: %d\tPoC: %s:%d\tN: %d\n",
				role.toString(), port, pocHost, pocPort, n));

		try {
			socket = new DatagramSocket(port);
		} catch(SocketException e) {
			String errmsg = String.format("Could not bind to port %d. Exiting (1)\n%s", port, e.getMessage());
			System.err.println(errmsg);
			System.exit(1);
		}

		Thread ringoThread = new Thread(new Ringo(role, port, pocHost, pocPort, n, socket, userCommandList));
		ringoThread.start();
		// Scanner scanner = new Scanner(System.in);
		// int numCommands = userCommandList.size();
		while (ringoThread.isAlive()) {
			// userCommandList.add(command);
			// if (numCommands > userCommandList.size()) {
				// System.out.println("Sorry but the system is busy processing another command. Try again later.");
			// }
		}
		// scanner.close();
		
		System.exit(0);
	}

	private static void parseArguments(String[] args) throws IllegalArgumentException {
		if (args.length < NUM_ARGS) {
			throw new IllegalArgumentException("At least four arguments must be given");
		}

		ArgumentChecker<String, Integer> intchecker = new ArgumentChecker<>((String arg) -> Integer.parseInt(arg));
		ArgumentChecker<String, String> hostchecker = new ArgumentChecker<>((String arg) -> (arg.equals("0") ? null : arg));

		role = Role.fromString(args[0]);
		port = intchecker.check(args[1], "Given Local Port isn't valid", 
			(Integer i) -> (i > 0) && (i <= MAX_PORT)
		);
		pocHost = hostchecker.check(args[2], "Given POC Host isn't valid", (String s) -> new Boolean(true));
		pocPort = intchecker.check(args[3], "Provided POC Port isn't valid", 
			(Integer i) -> 
				((pocHost == null && i == 0) || (pocHost != null && i > 0))
				&& (i >= 0)
				&& (i <= MAX_PORT)
		);
		n = intchecker.check(args[4], "Given N isn't valid", (Integer i) -> i > 0);
	}

	/**
	 * Print instructions on how to start this program
	 * @param stream Where to send the help statements
	 */
	private static void printHelp(PrintStream stream) {
		stream.println("Runtime Arguments:");
		stream.println("java Ringo <Role> <Local Port> <PoC Hostname> <PoC Port> <N>");
		stream.println("- Role: Sets this Ringo to be either a (S)ender, (R)eceiver, or a (F)orwarder");
		stream.println("- PoC Hostname: The Hostname of the Point of Contact. Set to 0 if no PoC");
		stream.println("- PoC Port: Port to use while contacting the PoC. Set to 0 if no PoC");
		stream.println("- N: Number of Ringos in the mesh");
		stream.println("");
	}
}

