import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class KeepAlive implements Runnable {
	public static final int KEEPALIVE_DELAY_MILLIS = 4000;
	
	private LinkedBlockingQueue<RingoPacket> inq;
	private LinkedBlockingQueue<RingoPacket> outq;
	private RingTracker tracker;
	private RingoPacketFactory factory;
	private Hashtable<HostInformation, Long> times;
	private TimerTask keepAliveTimer;
	private Timer timer;
	
	public KeepAlive(LinkedBlockingQueue<RingoPacket> inq, LinkedBlockingQueue<RingoPacket> outq, RingoPacketFactory factory, RingTracker tracker) {
		this.inq = inq;
		this.outq = outq;
		this.factory = factory;
		this.tracker = tracker;
		this.times = new Hashtable<>();
	}

	@Override
	public void run() {
		// Setup the times Hashtable
		Iterator<HostInformation> it = tracker.getHosts().iterator();
		while (it.hasNext()) {
			HostInformation host = it.next();
			times.put(host, System.currentTimeMillis());
		}
		
		this.keepAliveTimer = new KeepAliveTimerTask(this, outq, factory);
		this.timer = new Timer();
		timer.scheduleAtFixedRate(this.keepAliveTimer, KEEPALIVE_DELAY_MILLIS, KEEPALIVE_DELAY_MILLIS);
		
		while (true) {
			try {
				RingoPacket in = inq.take();
				HostInformation from = getHostFromFields(in.getSourceIP(), in.getSourcePort());
				if (from == null) {
					// handle later
					System.err.println("Invalid HostInformation Found: " + in.getSourceIP() + ":" + in.getSourcePort());
				} else {
					synchronized (times) {
						times.put(from, System.currentTimeMillis());
						times.put(getSelf(), System.currentTimeMillis());
					}
				}
			} catch (InterruptedException e) {
				System.err.println("Cannot continue processing Keep-Alive Events: Interrupted");
			}
		}
	}
	
	public synchronized ArrayList<HostInformation> getHosts() {
		return new ArrayList<HostInformation>(times.keySet());
	}
	
	public void update() {
		ArrayList<HostInformation> stale = new ArrayList<>();
		long staleTime = System.currentTimeMillis() - 2*KEEPALIVE_DELAY_MILLIS;
		synchronized (times) {
			Iterator<HostInformation> it = times.keySet().iterator();
			while (it.hasNext()) {
				HostInformation cur = it.next();
				
				if (times.get(cur).longValue() < staleTime) {
					stale.add(cur);
				}
			}
			
			times.keySet().forEach(host -> {
				HostState state = (stale.contains(host) ? HostState.DOWN : HostState.UP);
				tracker.updateHost(host, state);
			});
		}
		
		tracker.makeRingFromFilteredHosts();
	} 
	
	/**
	 * Searches hosts for a HostInformation that matches the parameters.
	 * Only call within a synchronized block or method
	 * 
	 * @param hostname Source Hostname from RingoPacket
	 * @param port Source Port from RingoPacket
	 * @return 
	 */
	private HostInformation getHostFromFields(String hostname, int port) {
		HostInformation ret = null;
		for (HostInformation host : times.keySet()) {
			if (host.hostString().equalsIgnoreCase(hostname + ":" + port)) {
				ret = host;
				break;
			}
		}
		return ret;
	}
	
	/**
	 * Searches hosts for a HostInformation that is local. There should only be one of these!
	 * Only call within a synchronized block or method
	 * @return
	 */
	private HostInformation getSelf() {
		HostInformation ret = null;
		for (HostInformation host : times.keySet()) {
			if (host.isLocal()) {
				ret = host;
				break;
			}
		}
		return ret;
	}
}
