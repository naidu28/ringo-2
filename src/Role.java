/**
 * Enum describing the network role of a Ringo.
 * 
 * @author sainaidu
 * @author andrewray
 */
public enum Role {
    FORWARDER("F", "Forwarder"),
    SENDER("S", "Sender"),
    RECEIVER("R", "Receiver");
	
	private String argName;
	private String name;
	
	Role(String shorthand, String longhand) {
		argName = shorthand;
		name = longhand;
	}
	
	public String toString() {
		return name;
	}
	
	public static Role fromString(String shortName) {
		switch (shortName) {
		case "f":
		case "F": return Role.FORWARDER;
		case "s":
		case "S": return Role.SENDER;
		case "r":
		case "R": return Role.RECEIVER;
		default:  throw new IllegalArgumentException(shortName + " is not a valid Role");
		}
	}
}