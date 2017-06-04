package com.gboxsw.acpmod.registry;

/**
 * Gateway to remote registers.
 */
public interface Gateway {

	/**
	 * Starts the gateway. Depending on implementation, the method can be
	 * blocking.
	 */
	public void start();

	/**
	 * Returns whether the gateway is running.
	 * 
	 * @return true, if the gateway is running.
	 */
	public boolean isRunning();

	/**
	 * Stops the gateway. Depending on implementation, the method can be
	 * blocking.
	 */
	public void stop();
}
