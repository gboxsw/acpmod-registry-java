package com.gboxsw.acpmod.registry;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Controller that manages updates of registers.
 */
public class AutoUpdater {

	/**
	 * Name of the update thread.
	 */
	private static final String THREAD_NAME = "Registry auto-update";

	/**
	 * Maximal duration of sleep in the update thread in milliseconds.
	 */
	private static final long MAX_THREAD_SLEEP = 100;

	/**
	 * Strategy that determines when hint requests are generated.
	 */
	public static enum HintStrategy {
		/**
		 * Hint request are generated in fixed period.
		 */
		SIMPLE,

		/**
		 * Hint request are generated in fixed period, however, if the hint
		 * response contains identifier of a managed register, new hint request
		 * is generated as soon as possible.
		 */
		SEMI_GREEDY,

		/**
		 * Hint request are generated in fixed period, however, if the hint
		 * response contains identifier of a register (eventually not managed by
		 * the updater), new hint request is generated as soon as possible.
		 */
		GREEDY
	}

	/**
	 * Configuration of method for retrieving update hints.
	 */
	public final static class HintSettings {
		/**
		 * Time in milliseconds between two hint readings (depending on
		 * strategy).
		 */
		private long interval = 1000;

		/**
		 * Timeout in milliseconds for completing a hint request.
		 */
		private long timeout = Register.DEFAULT_CONNECTION_SETTINGS.timeout;

		/**
		 * Strategy determining when hint requests are generated.
		 */
		private HintStrategy strategy = HintStrategy.SEMI_GREEDY;

		/**
		 * Returns time between two hint readings (depending on strategy).
		 * 
		 * @return time in milliseconds.
		 */
		public long getInterval() {
			return interval;
		}

		/**
		 * Sets time between two hint readings (depending on strategy).
		 * 
		 * @param interval
		 *            time in milliseconds.
		 */
		public void setInterval(long interval) {
			if (interval <= 0) {
				throw new IllegalArgumentException("Interval must be a positive number.");
			}

			this.interval = interval;
		}

		/**
		 * Returns time for completing a hint request.
		 * 
		 * @return time in milliseconds.
		 */
		public long getTimeout() {
			return timeout;
		}

		/**
		 * Sets time for completing a hint request.
		 * 
		 * @param timeout
		 *            time in milliseconds.
		 */
		public void setTimeout(long timeout) {
			if (interval <= 0) {
				throw new IllegalArgumentException("Interval must be a positive number.");
			}

			this.timeout = timeout;
		}

		/**
		 * Returns the strategy when hint requests are generated.
		 * 
		 * @return the strategy.
		 */
		public HintStrategy getStrategy() {
			return strategy;
		}

		/**
		 * Sets the strategy when hint requests are generated.
		 * 
		 * @param strategy
		 *            the desired strategy.
		 */
		public void setStrategy(HintStrategy strategy) {
			if (strategy == null) {
				throw new NullPointerException("Strategy cannot be null.");
			}

			this.strategy = strategy;
		}

		/**
		 * Creates clone.
		 * 
		 * @return the clone.
		 */
		private HintSettings createClone() {
			HintSettings result = new HintSettings();
			result.timeout = timeout;
			result.interval = interval;
			result.strategy = strategy;
			return result;
		}
	}

	/**
	 * State record related to registers of a register collection.
	 */
	private static class CollectionState {
		/**
		 * Collection of registers whose state is recorded.
		 */
		final WeakReference<RegisterCollection> registerCollection;

		/**
		 * Time of last reading of a register hint.
		 */
		long lastHintTime;

		/**
		 * Configuration of method for retrieving update hints or null, if use
		 * of hints is disabled.
		 */
		HintSettings hintSettings;

		/**
		 * Identifier of register that was notified as changed but is not
		 * managed (updated) by this updater.
		 */
		int unconfirmedRegisterId;

		/**
		 * Managed registers of the collection.
		 */
		final List<Register> registers = new ArrayList<>();

		/**
		 * Constructs data structure for managing registry hints.
		 * 
		 * @param collection
		 *            the register collection.
		 */
		public CollectionState(RegisterCollection collection, HintSettings hintSettings) {
			this.registerCollection = new WeakReference<RegisterCollection>(collection);
			this.hintSettings = hintSettings;
			this.unconfirmedRegisterId = -1;
			lastHintTime = MonotonicClock.INSTANCE.currentTimeMillis();
		}
	}

	/**
	 * Internal synchronization lock.
	 */
	private final Object lock = new Object();

	/**
	 * Set of managed registers.
	 */
	private final Set<Register> registers = new HashSet<Register>();

	/**
	 * Map that assigns to each registry collection its state data.
	 */
	private final WeakHashMap<RegisterCollection, CollectionState> collectionStates = new WeakHashMap<>();

	/**
	 * Thread realizing periodical update of managed registers.
	 */
	private Thread updateThread;

	/**
	 * Constructs a new register manager.
	 */
	public AutoUpdater() {
		// nothing to do
	}

	/**
	 * The updating subroutine (loop).
	 */
	private void mainLoop() {
		final Thread thisThread = Thread.currentThread();
		final ArrayList<Register> expiredRegisters = new ArrayList<Register>();
		final ArrayList<CollectionState> collectionsWithExpiredHints = new ArrayList<CollectionState>();

		while (true) {
			expiredRegisters.clear();
			collectionsWithExpiredHints.clear();
			long nextUpdate = Long.MAX_VALUE;

			synchronized (lock) {
				// check whether the update thread is active
				if (thisThread != updateThread) {
					break;
				}

				// find registers that require update
				for (Register register : registers) {
					long millisToUpdate = register.millisToNextUpdate();
					if (millisToUpdate <= 0) {
						expiredRegisters.add(register);
					} else {
						nextUpdate = Math.min(nextUpdate, millisToUpdate);
					}
				}

				// find register collections with active hints that require
				// update
				long now = MonotonicClock.INSTANCE.currentTimeMillis();
				for (CollectionState cs : collectionStates.values()) {
					if ((!cs.registers.isEmpty()) && (cs.hintSettings != null)) {
						long millisToUpdate = cs.hintSettings.interval - (now - cs.lastHintTime);
						if (millisToUpdate <= 0) {
							collectionsWithExpiredHints.add(cs);
						} else {
							nextUpdate = Math.min(nextUpdate, millisToUpdate);
						}
					}
				}

				// wait given amount of time if there are no expired registers
				// or pending hint requests
				if (expiredRegisters.isEmpty() && collectionsWithExpiredHints.isEmpty()) {
					try {
						synchronized (lock) {
							lock.wait(Math.min(nextUpdate, MAX_THREAD_SLEEP));
						}
					} catch (InterruptedException ignore) {
						// ignored exception
					}

					continue;
				}
			}

			// execute hint updates
			for (CollectionState cs : collectionsWithExpiredHints) {
				// retrieve timeout, id of unconfirmed register, and register
				// collections
				long operationTimeout;
				int unconfirmedRegisterId;
				HintStrategy hintStrategy;

				RegisterCollection registerCollection;
				synchronized (lock) {
					if (cs.hintSettings == null) {
						continue;
					}

					operationTimeout = cs.hintSettings.timeout;
					unconfirmedRegisterId = cs.unconfirmedRegisterId;
					cs.unconfirmedRegisterId = -1;
					registerCollection = cs.registerCollection.get();
					hintStrategy = cs.hintSettings.strategy;
					if (registerCollection == null) {
						cs.hintSettings = null;
						continue;
					}
				}

				// execute hint request
				int hintId = -1;
				try {
					hintId = registerCollection.getChangeHintId(unconfirmedRegisterId, operationTimeout);
					if (hintId < 0) {
						hintId = -1;
					}
				} catch (Exception ignore) {
					// in case of failure, hint request is skipped
				}

				// update timestamp of the last hint update and add all
				// registers with given register id to expired registers
				synchronized (lock) {
					boolean hintForManagedRegister = false;
					if (hintId >= 0) {
						for (Register register : cs.registers) {
							if (register.getRegisterId() == hintId) {
								hintForManagedRegister = true;
								if (!expiredRegisters.contains(register)) {
									expiredRegisters.add(register);
								}
							}
						}

						if (!hintForManagedRegister) {
							cs.unconfirmedRegisterId = hintId;
						}
					}

					// set time of last hint with respect to utilized strategy
					// (if time is not updated, new hint request is generated
					// in the next iteration of the main loop)
					if (hintStrategy == HintStrategy.SIMPLE) {
						cs.lastHintTime = MonotonicClock.INSTANCE.currentTimeMillis();
					} else if (hintStrategy == HintStrategy.SEMI_GREEDY) {
						if (!hintForManagedRegister) {
							cs.lastHintTime = MonotonicClock.INSTANCE.currentTimeMillis();
						}
					} else if (hintStrategy == HintStrategy.GREEDY) {
						if (hintId < 0) {
							cs.lastHintTime = MonotonicClock.INSTANCE.currentTimeMillis();
						}
					}
				}
			}

			// update registers if necessary
			for (Register register : expiredRegisters) {
				register.updateValue();
			}
		}
	}

	/**
	 * Add register to managed collection of registers.
	 * 
	 * @param register
	 *            the register to be added.
	 */
	public void addRegister(Register register) {
		if (register != null) {
			addRegisters(Collections.singletonList(register));
		}
	}

	/**
	 * Adds registers to managed collection of registers.
	 * 
	 * @param registers
	 *            the list of registers to be added.
	 */
	public void addRegisters(List<Register> registers) {
		if ((registers == null) || (registers.isEmpty())) {
			return;
		}

		synchronized (lock) {
			boolean changed = false;
			for (Register register : registers) {
				if (register == null) {
					continue;
				}

				if (this.registers.add(register)) {
					changed = true;

					// create collection state if necessary
					RegisterCollection registerCollection = register.getRegisterCollection();
					CollectionState collectionState = collectionStates.get(registerCollection);
					if (collectionState == null) {
						collectionState = new CollectionState(registerCollection, null);
						collectionStates.put(registerCollection, collectionState);
					}

					// add register to registers of underlying collection state
					collectionState.registers.add(register);
				}
			}

			if (changed) {
				createOrDestroyUpdateThread();
				lock.notifyAll();
			}
		}
	}

	/**
	 * Removes all registers from the managed collection.
	 */
	public void removeAllRegisters() {
		synchronized (lock) {
			if (registers.isEmpty()) {
				return;
			}

			registers.clear();
			for (CollectionState collectionState : collectionStates.values()) {
				collectionState.registers.clear();
			}

			lock.notifyAll();
			createOrDestroyUpdateThread();
		}
	}

	/**
	 * Removes a list of registers from the managed collection.
	 * 
	 * @param registers
	 *            the list of register to be removed.
	 */
	public void removeRegisters(List<Register> registers) {
		if ((registers == null) || (registers.isEmpty())) {
			return;
		}

		synchronized (lock) {
			boolean changed = false;
			for (Register register : registers) {
				if (register == null) {
					continue;
				}

				if (!this.registers.remove(register)) {
					continue;
				}

				CollectionState collectionState = collectionStates.get(register.getRegisterCollection());
				collectionState.registers.remove(register);
				changed = true;
			}

			if (changed) {
				lock.notifyAll();
				createOrDestroyUpdateThread();
			}
		}
	}

	/**
	 * Removes a register from the managed collection.
	 * 
	 * @param register
	 *            the register to be removed.
	 */
	public void removeRegister(Register register) {
		if (register != null) {
			removeRegisters(Collections.singletonList(register));
		}
	}

	/**
	 * Returns list of registers in this managed collection.
	 * 
	 * @return the list of registers.
	 */
	public List<Register> getRegisters() {
		synchronized (lock) {
			return new ArrayList<Register>(registers);
		}
	}

	/**
	 * Enables and configures method for retrieving update hints.
	 * 
	 * @param registerCollection
	 *            the remote collection of registers.
	 * @param settings
	 *            the settings.
	 */
	public void useRegistryHints(RegisterCollection registerCollection, HintSettings settings) {
		if (registerCollection == null) {
			return;
		}

		if (settings == null) {
			throw new NullPointerException("Hint settings cannot be null.");
		}

		synchronized (lock) {
			CollectionState collectionState = collectionStates.get(registerCollection);
			if (collectionState == null) {
				collectionState = new CollectionState(registerCollection, settings.createClone());
				collectionStates.put(registerCollection, collectionState);
			} else {
				collectionState.hintSettings = settings.createClone();
				collectionState.unconfirmedRegisterId = -1;
			}

			lock.notifyAll();
		}
	}

	/**
	 * Disable registry hints when updating registers from given register
	 * collection.
	 * 
	 * @param registerCollection
	 *            the remote collection of registers.
	 */
	public void disableRegistryHints(RegisterCollection registerCollection) {
		if (registerCollection == null) {
			return;
		}

		synchronized (lock) {
			CollectionState collectionState = collectionStates.get(registerCollection);
			if (collectionState != null) {
				collectionState.hintSettings = null;
				collectionState.unconfirmedRegisterId = -1;

				if (collectionState.registers.isEmpty()) {
					collectionStates.remove(registerCollection);
				}
			}

			lock.notifyAll();
		}
	}

	/**
	 * Creates or destroys the update thread. This method must be invoked from
	 * thread holding the lock.
	 */
	private void createOrDestroyUpdateThread() {
		// stop update thread, if the list of managed registers is empty
		if (registers.isEmpty() && (updateThread != null)) {
			updateThread.interrupt();
			updateThread = null;
			return;
		}

		// create update thread, if required.
		if ((!registers.isEmpty()) && (updateThread == null)) {
			updateThread = new Thread(new Runnable() {
				@Override
				public void run() {
					mainLoop();
				}
			}, THREAD_NAME);
			updateThread.setDaemon(true);
			updateThread.start();
		}
	}
}
