
//  public RingoPacket(String sourceIP, int sourcePort, String destIP, int destPort, int packetLength, int seqNum, PacketType type, Role role, int ringSize)
public class RingoPacketFactory {
	private String sourceIP;
	private int sourcePort;
	private Role myRole;
	private int ringSize;
	
	/**
	 * Class responsible for filling in the parameters of RingoPacket that are kept
	 * static throughout the execution of the program.
	 * 
	 * @param myIP IP Address of this Ringo
	 * @param myPort Port of this Ringo
	 * @param myRole Role of this Ringo
	 * @param ringSize 
	 */
	public RingoPacketFactory(String myIP, int myPort, Role myRole, int ringSize) {
		this.sourceIP = myIP;
		this.sourcePort = myPort;
		this.myRole = myRole;
		this.ringSize = ringSize;
	}
	
	public RingoPacket makePacket(String destIP, int destPort, int packetLength, int seqNum, PacketType type) {
		return new RingoPacket(sourceIP, sourcePort, destIP, destPort, packetLength, seqNum, type, myRole, ringSize);
	}

}
