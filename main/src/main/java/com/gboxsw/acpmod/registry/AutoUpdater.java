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
		 * Time in milliseconds between two hint readings.
		 */
		long hintInterval;

		/**
		 * Timeout in milliseconds for completing the hint request.
		 */
		long hintOperationTimeout;

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
		public CollectionState(RegisterCollection collection, long hintInterval, long hintTimeout) {
			this.registerCollection = new WeakReference<RegisterCollection>(collection);
			this.hintInterval = hintInterval;
			this.hintOperationTimeout = hintTimeout;
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
					if ((!cs.registers.isEmpty()) && (cs.hintInterval > 0)) {
						long millisToUpdate = cs.hintInterval - (now - cs.lastHintTime);
						if (millisToUpdate <= 0) {
							collectionsWithExpiredHints.add(cs);
						} else {
							nextUpdate = Math.min(nextUpdate, millisToUpdate);
						}
					}
				}

				// wait given amount of time if there are no expired registers
				// or hint requests
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

			// execute hint updates if necessary
			for (CollectionState cs : collectionsWithExpiredHints) {
				// retrieve timeout and register collections
				long operationTimeout;
				RegisterCollection registerCollection;
				synchronized (lock) {
					operationTimeout = cs.hintOperationTimeout;
					registerCollection = cs.registerCollection.get();
					if (registerCollection == null) {
						cs.hintInterval = 0;
						continue;
					}
				}

				// execute hint request
				int hintId = -1;
				try {
					hintId = registerCollection.getChangeHintId(operationTimeout);
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
					}

					// time delay between hint readings is applied only if the
					// hint was not useful
					if (!hintForManagedRegister) {
						cs.lastHintTime = MonotonicClock.INSTANCE.currentTimeMillis();
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
						collectionState = new CollectionState(registerCollection, 0, 0);
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
	 * Sets time interval in milliseconds between readings of hint.
	 * 
	 * @param registerCollection
	 *            the remote collection of registers.
	 * @param hintInterval
	 *            the interval in milliseconds or zero to disable use of hints.
	 * @param hintOperationTimeout
	 *            the timeout in milliseconds for completing the hint request.
	 */
	public void useRegistryHints(RegisterCollection registerCollection, long hintInterval, long hintOperationTimeout) {
		if (registerCollection == null) {
			return;
		}

		hintInterval = Math.max(0, hintInterval);
		hintOperationTimeout = Math.max(0, hintOperationTimeout);

		synchronized (lock) {
			CollectionState collectionState = collectionStates.get(registerCollection);
			if (collectionState == null) {
				collectionState = new CollectionState(registerCollection, hintInterval, hintOperationTimeout);
				collectionStates.put(registerCollection, collectionState);
			} else {
				collectionState.hintInterval = hintInterval;
				collectionState.hintOperationTimeout = hintOperationTimeout;
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
				collectionState.hintInterval = 0;

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
