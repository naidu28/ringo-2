import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

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
		HashSet<HostInformation> hosts = keepAlive.update();
		Iterator<HostInformation> it = hosts.iterator();
		try {
			while (it.hasNext()) {
				HostInformation host = it.next();
				if (host.isLocal())
					continue;
				
				out.put(createReq(host));
				if (host.getState() != HostState.UP) {
					for (int i = 0; i < TIMEOUT_REPEATS; i++)
						out.put(createReq(host));
				}
			}
		} catch (InterruptedException e) {
			System.out.println("Could not process Hosts queue in Keepalive Timer: Interrupted");
		}
	}
	
	private RingoPacket createReq(HostInformation info) {
		return factory.makePacket(info.getHost(), info.getPort(), 0, 0, PacketType.KEEPALIVE_REQ);
	}

}