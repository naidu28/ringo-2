import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class that calls update in KeepAlive, and sends KeepAlive packets to every Ringo it knows (even down ones!)
 * @author andrewray
 *
 */
public class KeepAliveTimerTask extends TimerTask {
	public  static final int TIMEOUT_REPEATS = 2;
	
	private RingoPacketFactory factory;
	private KeepAlive keepAlive;
	private LinkedBlockingQueue<RingoPacket> out;
	
	public KeepAliveTimerTask(KeepAlive keepAlive, LinkedBlockingQueue<RingoPacket> out, RingoPacketFactory factory) {
		this.keepAlive = keepAlive;
		this.factory = factory;
		this.out = out;
	}

	@Override
	public void run() {
		ArrayList<HostInformation> hosts = keepAlive.getHosts();
		hosts.forEach(host -> {
			try {
				out.put(createReq(host));
			} catch (InterruptedException e) {
				System.err.println("Unable to send KeepAlive notifications: Interrupted");
			}
		});
		keepAlive.update();
	}
	
	/**
	 * Creates a KEEPALIVE request packet that will be sent to to the Ringo
	 * @param info Ringo to send the KeepAlive packet to
	 * @return KeepAlive packet
	 */
	private RingoPacket createReq(HostInformation info) {
		return factory.makePacket(info.getHost(), info.getPort(), 0, 0, PacketType.KEEPALIVE);
	}

}