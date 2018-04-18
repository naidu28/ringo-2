public enum HostState {
	UP,
	MAYBE_DOWN,
	DOWN;
		
	/**
	 * Call when doing KeepAlive, and the request was ACK'd
	 * 
	 * @param original state to promote
	 * @return "Promoted" state of the HostInformation
	 */
	public static HostState promote(HostState original) {
		if (original == HostState.DOWN)
			return HostState.MAYBE_DOWN;
		else
			return HostState.UP;
	}
	
	/**
	 * Call when doing KeepAlive, and when request was not ACK'd
	 * 
	 * @param original State to demote
	 * @return "Demoted" state of the HostInformation
	 */
	public static HostState demote(HostState original) {
		if (original == HostState.UP)
			return HostState.MAYBE_DOWN;
		else
			return HostState.DOWN;
	}
}
