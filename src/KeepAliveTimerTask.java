import java.util.ArrayList;
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
	
	private RingoPacket createReq(HostInformation info) {
		return factory.makePacket(info.getHost(), info.getPort(), 0, 0, PacketType.KEEPALIVE);
	}

}