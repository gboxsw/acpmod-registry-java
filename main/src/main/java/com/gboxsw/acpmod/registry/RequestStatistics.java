package com.gboxsw.acpmod.registry;

/**
 * Statistics of requests. All methods are implemented as thread-safe.
 */
public class RequestStatistics {

	/**
	 * Total number of requests.
	 */
	private long totalRequests = 0;

	/**
	 * The number of failed requests.
	 */
	private long failedRequests = 0;

	/**
	 * Synchronization object.
	 */
	private final Object lock = new Object();

	/**
	 * Counts a request.
	 * 
	 * @param failed
	 *            true, if the request is failed, false otherwise.
	 */
	public void countRequest(boolean failed) {
		synchronized (lock) {
			totalRequests++;
			if (failed) {
				failedRequests++;
			}
		}
	}

	/**
	 * Resets statistics.
	 */
	public void reset() {
		synchronized (lock) {
			totalRequests = 0;
			failedRequests = 0;
		}
	}

	/**
	 * Returns total number of requests.
	 * 
	 * @return the total number of requests.
	 */
	public long getTotalRequests() {
		synchronized (lock) {
			return totalRequests;
		}
	}

	/**
	 * Returns the number of failed requests.
	 * 
	 * @return the number of failed requests.
	 */
	public long getFailedRequests() {
		synchronized (lock) {
			return failedRequests;
		}
	}

	/**
	 * Returns clone of statistics at given time (snapshot).
	 * 
	 * @return snapshot of current statistics.
	 */
	public RequestStatistics createSnapshot() {
		RequestStatistics result = new RequestStatistics();
		synchronized (lock) {
			result.totalRequests = totalRequests;
			result.failedRequests = failedRequests;
		}

		return result;
	}
}
