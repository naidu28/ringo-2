import java.net.DatagramPacket;
import java.util.Hashtable;

public class RingoPacket implements java.io.Serializable {

    public static final int MAX_PAYLOAD_SIZE = 512;

    private String sourceIP;
    private int sourcePort;
    private String destIP;
    private int destPort;
    private int packetLength;
    private int sequenceNumber;
    private PacketType type;
    private Role role;
    private Hashtable<String, Integer> lsa;
    private Hashtable<String, Integer> rttIndex;
    private Hashtable<Integer, String> indexRtt;
    private long [][] rtt;
    private long startTime;
    private long stopTime;
    private byte[] payload = new byte[MAX_PAYLOAD_SIZE];

    /**
     * RingoPacket converts a raw UDP packet into one that can be
     * understood by the rest of the Ringo application.
     *
     * A DatagramPacket can tell us which Ringo (IP Address/Port pair)
     * directly sent us a packet, or what will directly receive the
     * packet, but not any meta-information
     * @param basepacket Raw UDP Packet with valid payload
     */
    public RingoPacket(String sourceIP, int sourcePort, String destIP, int destPort, int packetLength, int seqNum, PacketType type, Role role) {
        // TODO:
    		this.sourceIP = sourceIP;
    		this.sourcePort = sourcePort;
    		this.destIP = destIP;
    		this.destPort = destPort;
    		this.packetLength = packetLength;
    		this.sequenceNumber = seqNum;
    		this.type = type;
    		this.role = role;
    		this.lsa = new Hashtable<String, Integer>();
    		this.rttIndex = new Hashtable<String, Integer>();
    		this.indexRtt = new Hashtable<Integer, String>();
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public String getDestIP() {
        return destIP;
    }

    public int getDestPort() {
        return destPort;
    }

    public int getPacketLength() {
        return packetLength;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public PacketType getType() {
        return type;
    }

    public Role getRole() {
        return role;
    }

    public void setLsa(Hashtable<String, Integer> lsa) {
    		this.lsa = lsa;
    }
    
    public Hashtable<String, Integer> getLsa() {
    		return this.lsa;
    }
    
    public void setRttIndex(Hashtable<String, Integer> rttIndex) {
    		this.rttIndex = rttIndex;
    }
    
    public Hashtable<String, Integer> getRttIndex() {
    		return this.rttIndex;
    }
    
    public void setIndexRtt(Hashtable<Integer, String> indexRtt) {
		this.indexRtt = indexRtt;
	}
	
	public Hashtable<Integer, String> getIndexRtt() {
		return this.indexRtt;
	}
    
    public void setRtt(long [][] rtt) {
		this.rtt = rtt;
	}
	
	public long[][] getRtt() {
		return this.rtt;
	}
    
    public void setStartTime(long time) {
    		this.startTime = time;
    }
    
    public long getStartTime() {
    		return this.startTime;
    }
    
    public void setStopTime(long time) {
		this.stopTime = time;
    }

	public long getStopTime() {
		return this.stopTime;
	}

    public byte[] getPayload() {
        return payload;
    }
    
    public boolean equals(Object other) {
    		if (other instanceof RingoPacket) {
    			RingoPacket packet = (RingoPacket) other;
    			return this.getSourceIP().equals(packet.getSourceIP()) && this.getDestIP().equals(packet.getDestIP()) && this.getSourcePort() == packet.getSourcePort() && this.getDestPort() == packet.getDestPort() && this.getType() == packet.getType() && this.getSequenceNumber() == packet.getSequenceNumber();
    		} else {
    			return false;
    		}
    }
    
    public void replace(RingoPacket packet) {
    		this.sourceIP = packet.getSourceIP();
    		this.sourcePort = packet.getSourcePort();
    		this.destIP = packet.getDestIP();
    		this.destPort = packet.getDestPort();
    		this.packetLength = packet.getPacketLength();
    		this.sequenceNumber = packet.getSequenceNumber();
    		this.type = packet.getType();
    		this.role = packet.getRole();
    		this.lsa = packet.getLsa();
    		this.rtt = packet.getRtt();
    		this.rttIndex = packet.getRttIndex();
    		this.indexRtt = packet.getIndexRtt();
    		this.startTime = packet.getStartTime();
    		this.stopTime = packet.getStopTime();
    		this.payload = packet.getPayload();
    }
    
    public static RingoPacket generateDatagram(
            String sendToIP,
            short sendToPort,
            String sourceIP,
            short sourcePort,
            String destIP,
            short destPort,
            short packetLength,
            short sequenceNumber,
            PacketType type,
            Role role,
            byte[] payload
    ) {
        return null;
    }
    
    public String toString() {
    		return "source: " + this.sourceIP + ":" + this.sourcePort + "\n" + "destination: " + this.destIP + ":" + this.destPort;
    }
}