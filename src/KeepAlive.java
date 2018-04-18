import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class KeepAlive implements Runnable {
	public static final int KEEPALIVE_DELAY_MILLIS = 3000;
	
	private LinkedBlockingQueue<RingoPacket> inq;
	private LinkedBlockingQueue<RingoPacket> outq;
	private RingTracker tracker;
	private RingoPacketFactory factory;
	private HashSet<HostInformation> hosts;
	private HashSet<HostInformation> toPromote;
	private TimerTask keepAliveTimer;
	private Timer timer;
	
	public KeepAlive(LinkedBlockingQueue<RingoPacket> inq, LinkedBlockingQueue<RingoPacket> outq, RingoPacketFactory factory, RingTracker tracker) {
		this.inq = inq;
		this.outq = outq;
		this.factory = factory;
		this.tracker = tracker;
	}

	@Override
	public void run() {
		this.hosts = new HashSet<>(tracker.getHosts());
		this.keepAliveTimer = new KeepAliveTimerTask(this, outq, factory);
		this.timer = new Timer();
		timer.schedule(this.keepAliveTimer, KEEPALIVE_DELAY_MILLIS);
		
		while (true) {
			try {
				RingoPacket in = inq.take();
				if (in.getType() == PacketType.KEEPALIVE_REQ) {
					// Another Ringo wants an ACK out of us. Send it!
					RingoPacket res = factory.makePacket(in.getSourceIP(), in.getSourcePort(), 0, 0, PacketType.KEEPALIVE_ACK);
					outq.put(res);
				} else if (in.getType() == PacketType.KEEPALIVE_ACK) {
					toPromote.add(getSelf());
					synchronized (hosts) {
						HostInformation other = getHostFromFields(in.getSourceIP(), in.getSourcePort());
						if (other != null)
							toPromote.add(other);
					}
				}
			} catch (InterruptedException e) {
				System.err.println("Cannot continue handling KeepAlive: Interrupted");
			}
		}
	}
	
	public HashSet<HostInformation> update() {
		synchronized (hosts) {
			handlePromotions();
			toPromote.clear();
			ArrayList<HostInformation> newhosts = new ArrayList<>(hosts);
			tracker.setHosts(newhosts);
			tracker.generateOptimalRing(newhosts);
			return (HashSet<HostInformation>) hosts.clone();
		}
	}
	
	private void handlePromotions() {
		@SuppressWarnings("unchecked")
		HashSet<HostInformation> toDemote = (HashSet<HostInformation>) hosts.clone();
		toDemote.removeAll(toPromote);
		
		HashSet<HostInformation> tmp = new HashSet<>();
		Iterator<HostInformation> it = hosts.iterator();
		while (it.hasNext()) {
			HostInformation host = it.next();
			
			// Check if we received any ACK packets. If we haven't, then we're down.
			if (host.isLocal() && toPromote.isEmpty())
				host.setState(HostState.DOWN);
			else if (toPromote.contains(host))
				host.setState(HostState.promote(host.getState()));
			else if (toDemote.contains(host))
				host.setState(HostState.demote(host.getState()));
			else {
				System.err.println("HandlePromotions: Unaccounted for state detected: Host not promotable nor demotable");
				System.err.println("\t" + host.toString());
			}
			
			tmp.add(host);
		}
		hosts = tmp;
	}
	
	private HostInformation getHostFromFields(String hostname, int port) {
		HostInformation ret = null;
		for (HostInformation host : hosts) {
			if (host.hostString().equalsIgnoreCase(hostname + ":" + port)) {
				ret = host;
				break;
			}
		}
		return ret;
	}
	
	private HostInformation getSelf() {
		HostInformation ret = null;
		for (HostInformation host : hosts) {
			if (host.isLocal()) {
				ret = host;
				break;
			}
		}
		return ret;
	}
}
