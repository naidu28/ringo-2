import java.lang.Thread;

public class App {	
    public static void main(String[] args) {
    		Role role;
    		if (args[0] == "S") {
    			role = Role.SENDER;
    		} else if (args[0] == "R") {
    			role = Role.RECEIVER;
    		} else {
    			role = Role.FORWARDER;
    		}
    		
    		Thread ringoThread = new Thread(new Ringo(role, Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4])));
    		ringoThread.start();
    		while (ringoThread.isAlive()) {}
    		
    		System.exit(0);
    }
}