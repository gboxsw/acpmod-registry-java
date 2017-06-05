package com.gboxsw.acpmod.registry;

/**
 * Local representation (view) of a remote register.
 */
public final class Register {

	/**
	 * The listener interface for receiving change events of registers.
	 */
	public interface ChangeListener {
		/**
		 * Invoked when the value of a register has been changed.
		 * 
		 * @param register
		 *            the register whose has been changed.
		 */
		void onChange(Register register);
	}

	/**
	 * Configuration parameters specifying how to handle communication to a
	 * remote registry.
	 */
	public static class ConnectionSettings {
		/**
		 * Maximal amount of time in milliseconds to complete read or write
		 * operation. Negative value or zero mean that there is no timeout to
		 * complete the operation.
		 */
		public final long timeout;

		/**
		 * Time in milliseconds after failed read, when next attempt to read the
		 * value is executed. Zero or negative value mean that there are no
		 * extra attempts to retry failed reads.
		 */
		public final long retryReadAfter;

		/**
		 * Number of failed attempts after which fail of read operation is
		 * promoted.
		 */
		public final byte attemptsToPromoteReadFail;

		/**
		 * Multiplication factor that is applied to retry period after each
		 * failed read in a row.
		 */
		public final double retryReadAfterFactor;

		/**
		 * Constructs new configuration settings.
		 * 
		 * @param timeout
		 *            the maximal amount of time in milliseconds to complete
		 *            read or write operation. Negative value or zero mean that
		 *            there is no timeout to complete the operation.
		 * @param retryReadAfter
		 *            time in milliseconds after failed read, when next attempt
		 *            to read the value is executed. Zero or negative value mean
		 *            that there are no extra attempts to retry failed reads.
		 * @param attemptsToPromoteReadFail
		 *            the number of failed attempts after which fail of read
		 *            operation is promoted.
		 * @param retryReadAfterFactor
		 *            the multiplication factor that is applied to retry period
		 *            after each failed read in a row.
		 */
		public ConnectionSettings(long timeout, long retryReadAfter, int attemptsToPromoteReadFail,
				double retryReadAfterFactor) {
			this.timeout = timeout;
			this.retryReadAfter = retryReadAfter;
			this.attemptsToPromoteReadFail = (byte) attemptsToPromoteReadFail;
			this.retryReadAfterFactor = retryReadAfterFactor;
		}

		public long getTimeout() {
			return timeout;
		}

		public long getRetryReadAfter() {
			return retryReadAfter;
		}

		public byte getAttemptsToPromoteReadFail() {
			return attemptsToPromoteReadFail;
		}

		public double getRetryReadAfterFactor() {
			return retryReadAfterFactor;
		}

		/**
		 * Creates new connection settings with changed value of timeout.
		 * 
		 * @param timeout
		 *            the desired value of timeout.
		 * @return the newly constructed connection settings.
		 */
		public ConnectionSettings withTimeout(long timeout) {
			return new ConnectionSettings(timeout, retryReadAfter, attemptsToPromoteReadFail, retryReadAfterFactor);
		}
	}

	/**
	 * Default connection settings of register.
	 */
	public static final ConnectionSettings DEFAULT_CONNECTION_SETTINGS = new ConnectionSettings(2000l, 250l, 2, 2.0);

	/**
	 * Name of the register.
	 */
	private String name;

	/**
	 * Description of the register.
	 */
	private String description;

	/**
	 * Associated remote register collection.
	 */
	private final RegisterCollection registerCollection;

	/**
	 * Identifier of the register.
	 */
	private final int registerId;

	/**
	 * The value of register retrieved from a remote register during last
	 * update.
	 */
	private Object value;

	/**
	 * The last valid value retrieved from a remote register.
	 */
	private Object lastValidValue;

	/**
	 * Indicates whether the register is read-only.
	 */
	private final boolean readOnly;

	/**
	 * Synchronization object for thread-safe access.
	 */
	private final Object lock = new Object();

	/**
	 * Time of the last value update.
	 */
	private long updateTimeMillis = Long.MIN_VALUE;

	/**
	 * Update interval in milliseconds.
	 */
	private long updateInterval = 1000;

	/**
	 * Codec for transforming remote values to local values and vice versa.
	 */
	private final Codec codec;

	/**
	 * Indicates whether the register operates in binary mode.
	 */
	private final boolean binaryMode;

	/**
	 * Connection settings specifying how to handle communication with a remote
	 * register.
	 */
	private ConnectionSettings connectionSettings;

	/**
	 * Number of read fails in a row;
	 */
	private int readFailsInRow = 0;

	/**
	 * The listener consuming change events.
	 */
	private ChangeListener changeListener;

	/**
	 * Constructs register of a new connector.
	 * 
	 * @param registerCollection
	 *            the remote register collection that provides access to remote
	 *            registers.
	 * @param registerId
	 *            the identifier of register.
	 * @param readOnly
	 *            indicates that the register is read only.
	 * @param codec
	 *            the codec used to decode/encode value of register.
	 */
	public Register(RegisterCollection registerCollection, int registerId, boolean readOnly, Codec codec) {
		if (registerCollection == null) {
			throw new NullPointerException("Connector cannot be null.");
		}

		if (registerId < 0) {
			throw new IllegalArgumentException("Invalid ID of register.");
		}

		if (codec == null) {
			throw new NullPointerException("Codec cannot be null.");
		}

		this.binaryMode = (codec instanceof Codec.BinaryCodec);
		this.registerCollection = registerCollection;
		this.registerId = registerId;
		this.readOnly = readOnly;
		this.codec = codec;
		this.connectionSettings = DEFAULT_CONNECTION_SETTINGS;
	}

	/**
	 * Updates value of the register. The method newer throws an exception as a
	 * result of update (however, an exception can be thrown from change
	 * listener).
	 */
	public void updateValue() {
		Object oldValue = value;
		ChangeListener listener = null;
		try {
			// retrieve and decode value from remote register
			Object newValue = null;
			if (binaryMode) {
				byte[] remoteValue = registerCollection.readBinaryRegister(registerId, connectionSettings.timeout);
				newValue = ((Codec.BinaryCodec) codec).decodeRemoteBinaryValue(remoteValue);
			} else {
				int remoteValue = registerCollection.readIntegerRegister(registerId, connectionSettings.timeout);
				newValue = ((Codec.IntCodec) codec).decodeRemoteIntValue(remoteValue);
			}

			if (newValue == null) {
				throw new RuntimeException("Invalid result of decoding.");
			}

			// update local value
			synchronized (lock) {
				value = newValue;
				lastValidValue = value;
				updateTimeMillis = MonotonicClock.INSTANCE.currentTimeMillis();
				readFailsInRow = 0;
				if (!value.equals(oldValue)) {
					listener = changeListener;
				}
			}
		} catch (Exception e) {
			synchronized (lock) {
				readFailsInRow++;

				// invalidate value after failed read (if required)
				if (readFailsInRow >= connectionSettings.attemptsToPromoteReadFail) {
					value = null;
					if (oldValue != null) {
						listener = changeListener;
					}
				}

				updateTimeMillis = MonotonicClock.INSTANCE.currentTimeMillis();
			}
		}

		if (listener != null) {
			listener.onChange(this);
		}
	}

	/**
	 * Returns milliseconds remaining to next update of value. The value 0
	 * indicates that update must be executed immediately.
	 * 
	 * @return the number of milliseconds to next update.
	 */
	public long millisToNextUpdate() {
		synchronized (lock) {
			long currentUpdateInterval = updateInterval;

			// compute update interval in case of failed reads
			if ((readFailsInRow > 0) && (connectionSettings.retryReadAfter > 0)) {
				double retryInterval = connectionSettings.retryReadAfter;

				// apply a deterministic backoff strategy
				if (connectionSettings.retryReadAfterFactor >= 1) {
					for (int i = 1; i < readFailsInRow; i++) {
						retryInterval *= connectionSettings.retryReadAfterFactor;
						if (retryInterval > updateInterval) {
							break;
						}
					}
				}

				currentUpdateInterval = Math.min(updateInterval, Math.round(retryInterval));
			}

			final long timeFromLastUpdate = MonotonicClock.INSTANCE.currentTimeMillis() - updateTimeMillis;
			if ((timeFromLastUpdate < 0) || (timeFromLastUpdate >= currentUpdateInterval)) {
				return 0;
			}

			return currentUpdateInterval - timeFromLastUpdate;
		}
	}

	/**
	 * Returns the type (class) of register values.
	 * 
	 * @return the type.
	 */
	public Class<?> getType() {
		return codec.getValueType();
	}

	/**
	 * Returns the current value of the register.
	 * 
	 * @return the current value of register, or null, if the value of register
	 *         is invalid.
	 */
	public Object getValue() {
		synchronized (lock) {
			return value;
		}
	}

	/**
	 * Set new value of the register.
	 * 
	 * @param newValue
	 *            the desired value of register.
	 */
	public void setValue(Object newValue) {
		if (readOnly) {
			throw new UnsupportedOperationException("Value of read-only register cannot be changed.");
		}

		try {
			if (binaryMode) {
				byte[] valueToSend = ((Codec.BinaryCodec) codec).encodeToBinaryValue(newValue);
				registerCollection.writeBinaryRegister(registerId, valueToSend, connectionSettings.timeout);
			} else {
				int valueToSend = ((Codec.IntCodec) codec).encodeToIntValue(newValue);
				registerCollection.writeIntegerRegister(registerId, valueToSend, connectionSettings.timeout);
			}
		} catch (Exception e) {
			throw new RuntimeException("Change of registry failed.", e);
		} finally {
			updateValue();
		}
	}

	/**
	 * Returns the last valid value of the register.
	 * 
	 * @return the last valid value of the register.
	 */
	public Object getLastValidValue() {
		synchronized (lock) {
			return lastValidValue;
		}
	}

	/**
	 * Returns the name of register.
	 * 
	 * @return the name.
	 */
	public String getName() {
		synchronized (lock) {
			return name;
		}
	}

	/**
	 * Sets the name of register.
	 * 
	 * @param name
	 *            the desired name of register.
	 */
	public void setName(String name) {
		synchronized (lock) {
			this.name = name;
		}
	}

	/**
	 * Returns the description of register.
	 * 
	 * @return the description.
	 */
	public String getDescription() {
		synchronized (lock) {
			return description;
		}
	}

	/**
	 * Sets the description of register.
	 * 
	 * @param description
	 *            the description.
	 */
	public void setDescription(String description) {
		synchronized (lock) {
			this.description = description;
		}
	}

	/**
	 * Returns the time of the last attempt to update the value.
	 * 
	 * @return the time in milliseconds of the last attempt to update the value.
	 */
	public long getUpdateTimeMillis() {
		synchronized (lock) {
			return updateTimeMillis;
		}
	}

	/**
	 * Returns the interval in milliseconds between two updates of the register.
	 * 
	 * @return the interval in milliseconds.
	 */
	public long getUpdateInterval() {
		synchronized (lock) {
			return updateInterval;
		}
	}

	/**
	 * Sets the interval in milliseconds between two updates of the register.
	 * 
	 * @param updateInterval
	 *            time in milliseconds between two updates of the register.
	 */
	public void setUpdateInterval(long updateInterval) {
		if (updateInterval <= 0) {
			throw new IllegalArgumentException("Update interval of the register must be a non-zero positive value.");
		}

		synchronized (lock) {
			this.updateInterval = updateInterval;
		}
	}

	/**
	 * Returns the connection settings.
	 * 
	 * @return the connection settings of the register.
	 */
	public ConnectionSettings getConnectionSettings() {
		synchronized (lock) {
			return connectionSettings;
		}
	}

	/**
	 * Sets the connection settings.
	 * 
	 * @param connectionSettings
	 *            the desired connection settings.
	 */
	public void setConnectionSettings(ConnectionSettings connectionSettings) {
		if (connectionSettings == null) {
			throw new NullPointerException("Connection settings cannot be null.");
		}

		synchronized (lock) {
			this.connectionSettings = connectionSettings;
		}
	}

	/**
	 * Return the listener invoked when the value of the register changed.
	 * 
	 * @return the listener.
	 */
	public ChangeListener getChangeListener() {
		synchronized (lock) {
			return changeListener;
		}
	}

	/**
	 * Sets the listener invoked when the value of register changed.
	 * 
	 * @param changeListener
	 *            the desired listener of value change event.
	 */
	public void setChangeListener(ChangeListener changeListener) {
		synchronized (lock) {
			this.changeListener = changeListener;
		}
	}

	/**
	 * Returns the remote register collection used to read and write register
	 * values.
	 * 
	 * @return the remote registry collection providing access to the remote
	 *         register.
	 */
	public RegisterCollection getRegisterCollection() {
		return registerCollection;
	}

	/**
	 * Returns an ID of the register.
	 * 
	 * @return the ID of the register.
	 */
	public int getRegisterId() {
		return registerId;
	}

	/**
	 * Returns whether the register is read-only.
	 * 
	 * @return true, if the register is read-only.
	 */
	public boolean isReadOnly() {
		return readOnly;
	}
}
