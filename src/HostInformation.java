public class HostInformation {
	
	private HostState state;
	private boolean local;
	private String host;
	private int port;

	public HostInformation(HostState state, boolean local, String host, int port) {
		this.state = state;
		this.local = local;
		this.host = host; 
		this.port = port;
	}

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
	
	public int hashCode() {
		return hostString().hashCode();
	}

}
