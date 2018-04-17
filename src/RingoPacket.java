import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Hashtable;

/**
 * All network communications on the Ringo protocol use
 * the RingoPacket class to store, send, and receive information.
 * This packet implements interface "Serializable" to allow
 * it to be byte-encoded and effectively stored in the data
 * buffer of a UDP/Datagram packet wrapper.
 * 
 * All headers for a RingoPacket are specified as class fields.
 * 
 * @author sainaidu
 * @author andrewray
 */
public class RingoPacket implements java.io.Serializable {

    public static final int MAX_PAYLOAD_SIZE = 512;

    private String sourceIP;
    private int sourcePort;
    private String destIP;
    private int destPort;
    private long sequenceLength;
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
	 * Serialization is the process of converting a Java object into
	 * bytecode. This is necessary for us to send Java objects over
	 * the network.
	 * 
	 * @param obj - serializable object (in this project, only RingoPacket)
	 * @return byte [] - bytecode representation of the object
	 */
	public static byte [] serialize(Object obj) {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		byte [] serializedObj = null;

		try {
			ObjectOutputStream so = new ObjectOutputStream(bo);
			so.writeObject(obj);
			so.flush();
			serializedObj = bo.toByteArray();
		} catch (IOException e) {
			// handle later
		}

		return serializedObj;
	}
    
    /**
	 * Deserialization is the process of converting a segment of
	 * serialized bytecode into a valid Java object. This is the 
	 * reverse of the serialization process.
	 * 
	 * @param b [] - bytecode representation of the object obj - 
	 * @return RingoPacket - deserialized object (in this project, only RingoPacket)
	 */
	public static RingoPacket deserialize(byte [] b) {
		RingoPacket obj = null;

		ByteArrayInputStream bi = new ByteArrayInputStream(b);

		try {
			ObjectInputStream si = new ObjectInputStream(bi);
			obj = (RingoPacket) si.readObject();
		} catch (Exception e) {
			// handle later
			System.out.println("this exception: " +e);
		}

		return obj;
	}

    /**
     * RingoPacket converts a raw UDP packet into one that can be
     * understood by the rest of the Ringo application.
     *
     * A DatagramPacket can tell us which Ringo (IP Address/Port pair)
     * directly sent us a packet, or what will directly receive the
     * packet, but not any meta-information
     * @param basepacket Raw UDP Packet with valid payload
     */
    public RingoPacket(String sourceIP, int sourcePort, String destIP, int destPort, long seqLength, int seqNum, PacketType type, Role role, int ringSize) {
        // TODO:
    		this.sourceIP = sourceIP;
    		this.sourcePort = sourcePort;
    		this.destIP = destIP;
    		this.destPort = destPort;
    		this.sequenceLength = seqLength;
    		this.sequenceNumber = seqNum;
    		this.type = type;
    		this.role = role;
    		this.lsa = new Hashtable<String, Integer>();
    		this.rtt = new long[ringSize][ringSize];
    		for (int i = 0; i < ringSize; i++) {
    			for (int j = 0; j < ringSize; j++) {
    				this.rtt[i][j] = -1;
    			}
    		}
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
    
    public void setSourceIP(String ip) {
        this.sourceIP = ip;
    }

    public void setSourcePort(int port) {
        this.sourcePort = port;
    }

    public void setDestIP(String ip) {
        this.destIP = ip;
    }

    public void setDestPort(int port) {
        this.destPort = port;
    }

    public long getSequenceLength() {
        return sequenceLength;
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

	public void setPayload(byte [] payload) {
		this.payload = payload;
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
    		this.sequenceLength = packet.getSequenceLength();
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
    
    public String toString() {
    		return "source: " + this.sourceIP + ":" + this.sourcePort + "\n" + "destination: " + this.destIP + ":" + this.destPort + "\ntype: " + this.getType() + "\n";
    }
}