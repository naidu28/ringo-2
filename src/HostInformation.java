public class HostInformation {
	
	private HostState state;
	private boolean local;
	private String host;
	private int port;

	/**
	 * A Partially immutable representation of the members of the Ring. Only state is mutable.
	 * 
	 * @param state MUTABLE state of Ringo
	 * @param local does this HostInformation represent THIS Ringo/David?
	 * @param host 
	 * @param port 
	 */
	public HostInformation(HostState state, boolean local, String host, int port) {
		this.state = state;
		this.local = local;
		this.host = host; 
		this.port = port;
	}

	/**
	 * A host is considered alive, even if it failed one round of KeepAlive.
	 * This is not necessarily the case for the local Ringo
	 * @return true if the Host is up
	 */
	public boolean isActive() {
		return !(state == HostState.DOWN);
	}
	
	public HostState getState() {
		return this.state;
	}

	public void setState(HostState newstate) {
		this.state = newstate;
	}

	public boolean isLocal() {
		return local;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}
	
	/**
	 * 
	 * @return The hostname and port of the Ringo, in hostname:port layout
	 */
	public String hostString() {
		return host + ":" + port;
	}
	
	public String toString() {
		String activeString = state.name();
		String localString = (local) ? "local" : "remote";
		
		return "["
			+ hostString() + ",\t"
			+ activeString + ",\t"
			+ localString + "]";
	}
	
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null || o.getClass() != this.getClass())
			return false;
		
		HostInformation other = (HostInformation) o;
		if (state == other.getState()
			&& local == other.isLocal()
			&& host.equalsIgnoreCase(other.getHost())
			&& port == other.getPort()) {		
			return true;
		}
		
		return false;
	}
	
	/**
	 * The only important part of this class is the hostname and port. The rest is metadata.
	 */
	public int hashCode() {
		return hostString().hashCode();
	}

}
