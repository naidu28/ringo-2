/**
 * Enum describing the type of a RingoPacket
 * 
 * @author sainaidu
 * @author andrewray
 */
public enum PacketType {
	SYN,
	ACK,
    LSA,
    LSA_COMPLETE,
    RTT_REQ,
    RTT_RES,
    RTT_COMPLETE,
    KEEPALIVE_REQ,
    KEEPALIVE_ACK,
    DATA,
    PING_REQ,
    PING_RES,
    PING_COMPLETE
}