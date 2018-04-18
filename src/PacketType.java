/**
 * Enum describing the type of a RingoPacket
 * 
 * @author sainaidu
 * @author andrewray
 */
public enum PacketType {
	INIT_REQ,
	INIT_RES,
	SYN,
	ACK,
    LSA,
    LSA_COMPLETE,
    RTT_REQ,
    RTT_RES,
    RTT_COMPLETE,
    KEEPALIVE,
    DATA,
    PING_REQ,
    PING_RES,
    PING_COMPLETE
}