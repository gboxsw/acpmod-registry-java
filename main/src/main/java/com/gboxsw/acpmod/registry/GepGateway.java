package com.gboxsw.acpmod.registry;

import java.util.*;

import com.gboxsw.acpmod.gep.GEPMessenger;
import com.gboxsw.acpmod.gep.GEPMessenger.*;

/**
 * Gateway using GEP protocol that communicates with multiple register
 * collections available in the RS485 network.
 */
public final class GepGateway implements Gateway {

	/**
	 * Code of request for reading value of an integer register.
	 */
	private final static int READ_INT_REGISTRY_REQUEST = 0x01;

	/**
	 * Code of Request for writing value to an integer register.
	 */
	private final static int WRITE_INT_REGISTRY_REQUEST = 0x02;

	/**
	 * Code of request for reading value of a binary register.
	 */
	private final static int READ_BIN_REGISTRY_REQUEST = 0x03;

	/**
	 * Code of Request for writing value to a binary register.
	 */
	private final static int WRITE_BIN_REGISTRY_REQUEST = 0x04;

	/**
	 * Code of Request for retrieving change hint - identifier of register whose
	 * value has changed after last reading of its value.
	 */
	private final static int GET_CHANGE_HINT_REQUEST = 0x05;

	/**
	 * Code of response indicating an unknown request or failed request.
	 */
	@SuppressWarnings("unused")
	private final static int REQUEST_FAILED_RESPONSE = 0x00;

	/**
	 * Code of response indicating that request was completed.
	 */
	private final static int REQUEST_OK_RESPONSE = 0x01;

	/**
	 * Code of response indicating that write request failed due to unwritable
	 * register.
	 */
	@SuppressWarnings("unused")
	private final static int UNWRITABLE_REGISTER_RESPONSE = 0x02;

	/**
	 * Array containing a prepared change hint request.
	 */
	private final static byte[] PREPARED_GET_CHANGE_HINT_REQUEST = new byte[] { GET_CHANGE_HINT_REQUEST };

	/**
	 * An empty payload.
	 */
	private final static byte[] EMPTY_PAYLOAD = new byte[0];

	/**
	 * Remote collection of registers provided by a single device in a GEP based
	 * network of devices/targets.
	 */
	private class GepRegisterCollection implements RegisterCollection {

		/**
		 * Identifier of the registry (destination ID for GEP messages)
		 */
		private final int registryId;

		/**
		 * Statistics of requests.
		 */
		private final RequestStatistics statistics = new RequestStatistics();

		@Override
		public int getChangeHintId(int confirmedRegisterId, long timeout) {
			try {
				int result = GepGateway.this.getChangeHint(registryId, confirmedRegisterId, timeout);
				statistics.countRequest(false);
				return result;
			} catch (Exception e) {
				statistics.countRequest(true);
				throw e;
			}
		}

		@Override
		public int readIntegerRegister(int registerId, long timeout) throws RuntimeException {
			try {
				int result = GepGateway.this.readIntegerRegister(registryId, registerId, timeout);
				statistics.countRequest(false);
				return result;
			} catch (Exception e) {
				statistics.countRequest(true);
				throw e;
			}
		}

		@Override
		public void writeIntegerRegister(int registerId, int value, long timeout) throws RuntimeException {
			try {
				GepGateway.this.writeIntegerRegister(registryId, registerId, value, timeout);
				statistics.countRequest(false);
			} catch (Exception e) {
				statistics.countRequest(true);
				throw e;
			}
		}

		@Override
		public byte[] readBinaryRegister(int registerId, long timeout) throws RuntimeException {
			try {
				byte[] result = GepGateway.this.readBinaryRegister(registryId, registerId, timeout);
				statistics.countRequest(false);
				return result;
			} catch (Exception e) {
				statistics.countRequest(true);
				throw e;
			}
		}

		@Override
		public void writeBinaryRegister(int registerId, byte[] value, long timeout) throws RuntimeException {
			try {
				GepGateway.this.writeBinaryRegister(registryId, registerId, value, timeout);
				statistics.countRequest(false);
			} catch (Exception e) {
				statistics.countRequest(true);
				throw e;
			}
		}

		private GepRegisterCollection(int registryId) {
			this.registryId = registryId;
		}

		@Override
		public RequestStatistics getStatistics() {
			return statistics;
		}

		@Override
		public Gateway getGateway() {
			return GepGateway.this;
		}
	}

	/**
	 * Messenger that allows communication with a remote registry using GEP
	 * protocol.
	 */
	private final GEPMessenger messenger;

	/**
	 * Map of created registry collections.
	 */
	private final Map<Integer, GepRegisterCollection> registerCollections = new HashMap<>();

	/**
	 * Counter to generate "unique" request tags.
	 */
	private int tagCounter = 0;

	/**
	 * Tag associated to pending (open, not completed) request.
	 */
	private int tagOfOpenRequest = -1;

	/**
	 * Response to request.
	 */
	private byte[] receivedResponse = null;

	/**
	 * Internal lock that manages processing received messages.
	 */
	private final Object requestLock = new Object();

	/**
	 * Internal synchronization lock that ensures that all operations are
	 * executed in a serial order.
	 */
	private final Object serialOrderLock = new Object();

	/**
	 * Constructs new gateway to registers which is based on GEP messenger.
	 * 
	 * @param socket
	 *            the stream socket providing access to remote side or network.
	 * @param messengerId
	 *            the identifier of the messenger applied to filter incoming
	 *            messages. Allowed values are between 0 and 15, if the value is
	 *            0, all messages are received.
	 * @param useDaemonThread
	 *            the setting that specifies whether underlying communication
	 *            thread is marked as a daemon thread or a user thread.
	 */
	public GepGateway(FullDuplexStreamSocket socket, int messengerId, boolean useDaemonThread) {
		messenger = new GEPMessenger(socket, messengerId, 30, new MessageListener() {
			@Override
			public void onMessageReceived(int tag, byte[] message) {
				handleMessage(tag, message);
			}
		});

		messenger.setDaemon(useDaemonThread);
	}

	/**
	 * Sets the initial delay in a new session.
	 * 
	 * @param initialDelay
	 *            the initial delay in milliseconds.
	 * 
	 * @see GEPMessenger#setInitialDelay(long)
	 */
	public void setInitialDelay(long initialDelay) {
		messenger.setInitialDelay(initialDelay);
	}

	/**
	 * Returns the initial delay in a new session.
	 * 
	 * @return the initial delay in milliseconds.
	 * 
	 * @see GEPMessenger#getInitialDelay()
	 */
	public long getInitialDelay() {
		return messenger.getInitialDelay();
	}

	/**
	 * Starts the session ensuring GEP connection to available remote register
	 * collections.
	 */
	@Override
	public void start() {
		synchronized (serialOrderLock) {
			try {
				messenger.start(true);
			} catch (Exception e) {
				throw new RuntimeException("Start of session (based on GEP messenger) failed.", e);
			}
		}
	}

	@Override
	public boolean isRunning() {
		return messenger.isRunning();
	}

	/**
	 * Stops the session ensuring GEP connection to available remote register
	 * collections.
	 * 
	 * @param blocked
	 *            true, if the current thread should be blocked until the
	 *            session terminates, false otherwise.
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread during
	 *             waiting for stopping of the session.
	 */
	public void stop(boolean blocked) throws InterruptedException {
		synchronized (serialOrderLock) {
			try {
				messenger.stop(blocked);
			} catch (InterruptedException e) {
				throw e;
			} catch (Exception e) {

			}
		}
	}

	/**
	 * Stops the session without waiting for termination.
	 */
	public void stop() {
		try {
			stop(false);
		} catch (InterruptedException ignore) {
			// ignored
		}
	}

	/**
	 * Returns a remote register collection to a collection defined by given
	 * (GEP) ID.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages)
	 * @return the remote register collection.
	 */
	public RegisterCollection getRegisterCollection(int registryId) {
		if ((registryId < 0) || (registryId > 15)) {
			throw new RuntimeException("Invalid registry identifier (allowed: 0-15)");
		}

		synchronized (registerCollections) {
			GepRegisterCollection registerCollection = registerCollections.get(registryId);
			if (registerCollection == null) {
				registerCollection = new GepRegisterCollection(registryId);
				registerCollections.put(registryId, registerCollection);
			}

			return registerCollection;
		}
	}

	/**
	 * Executes retrieval of change hint.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages).
	 * @param confirmedRegisterId
	 *            the identifier of a register which is confirmed by the client
	 *            as read without executing a real reading of a value. If the
	 *            identifier is negative, no register is confirmed.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            operation.
	 * @return the hint (identifier of a modifier register).
	 */
	public int getChangeHint(int registryId, int confirmedRegisterId, long timeout) {
		if (confirmedRegisterId >= 0) {
			checkRegisterId(confirmedRegisterId);
		}

		// prepare request
		byte[] request = (confirmedRegisterId < 0) ? PREPARED_GET_CHANGE_HINT_REQUEST
				: buildRequest(GET_CHANGE_HINT_REQUEST, confirmedRegisterId, null);

		// send request and process response
		try {
			byte[] response = sendRequest(registryId, request, timeout);
			checkResponse(response);

			return decodeNumber(response, 1);
		} catch (Exception e) {
			throw new RuntimeException("Retrieval of change hint failed.", e);
		}
	}

	/**
	 * Executes read of an integer register.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages).
	 * @param registerId
	 *            the identifier of the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            operation.
	 * @return the value of register.
	 * 
	 * @throws RuntimeException
	 *             when operation failed.
	 */
	private int readIntegerRegister(int registryId, int registerId, long timeout) throws RuntimeException {
		checkRegisterId(registerId);

		// prepare message with request
		byte[] request = buildRequest(READ_INT_REGISTRY_REQUEST, registerId, null);

		// send request and process response
		try {
			byte[] response = sendRequest(registryId, request, timeout);
			checkResponse(response);

			return decodeNumber(response, 1);
		} catch (Exception e) {
			throw new RuntimeException("Read operation failed.", e);
		}
	}

	/**
	 * Executes write to an integer register.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages).
	 * @param registerId
	 *            the identifier of the register.
	 * @param value
	 *            the integer value to be written.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            operation.
	 * 
	 * @throws RuntimeException
	 *             when operation failed.
	 */
	private void writeIntegerRegister(int registryId, int registerId, int value, long timeout) throws RuntimeException {
		checkRegisterId(registerId);

		// prepare message with request
		byte[] request = buildRequest(WRITE_INT_REGISTRY_REQUEST, registerId, encodeNumber(value));

		// send request and process response
		try {
			byte[] response = sendRequest(registryId, request, timeout);
			checkResponse(response);
		} catch (Exception e) {
			throw new RuntimeException("Write operation failed.", e);
		}
	}

	/**
	 * Executes read of a binary register.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages).
	 * @param registerId
	 *            the identifier of the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            operation.
	 * @return the value of register.
	 * 
	 * @throws RuntimeException
	 *             when operation failed.
	 */
	public byte[] readBinaryRegister(int registryId, int registerId, long timeout) {
		checkRegisterId(registerId);

		// prepare message with request
		byte[] request = buildRequest(READ_BIN_REGISTRY_REQUEST, registerId, null);

		// send request and process response
		try {
			byte[] response = sendRequest(registryId, request, timeout);
			checkResponse(response);

			byte[] result = new byte[response.length - 1];
			System.arraycopy(response, 1, result, 0, result.length);
			return result;
		} catch (Exception e) {
			throw new RuntimeException("Read operation failed.", e);
		}
	}

	/**
	 * Executes write of a binary register.
	 * 
	 * @param registryId
	 *            the identifier of registry (destination ID of GEP messages).
	 * @param registerId
	 *            the identifier of the register.
	 * @param value
	 *            the value to be written.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            operation.
	 */
	public void writeBinaryRegister(int registryId, int registerId, byte[] value, long timeout) {
		checkRegisterId(registerId);

		// prepare message with request
		byte[] request = buildRequest(WRITE_BIN_REGISTRY_REQUEST, registerId, value);

		// send request and process response
		try {
			byte[] response = sendRequest(registryId, request, timeout);
			checkResponse(response);
		} catch (Exception e) {
			throw new RuntimeException("Write operation failed.", e);
		}
	}

	/**
	 * Sends a request and waits for response.
	 * 
	 * @param request
	 *            the encoded request.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            request, i.e. to receive the response. Zero or negative value
	 *            mean that there is no timeout defined for the operation.
	 * @return the encoded response or null, if no response was received.
	 * 
	 * @throws RuntimeException
	 *             when operation failed.
	 */
	private byte[] sendRequest(int registryId, byte[] request, long timeout) throws RuntimeException {
		synchronized (serialOrderLock) {
			try {
				// initialize
				int messageTag;
				synchronized (requestLock) {
					tagCounter = (tagCounter + 1) % 1000;
					tagOfOpenRequest = tagCounter;
					messageTag = tagOfOpenRequest;
					receivedResponse = null;
				}

				long operationStart = MonotonicClock.INSTANCE.currentTimeMillis();

				// send message with request
				if (!messenger.sendMessage(registryId, request, messageTag)) {
					throw new RuntimeException("Sending of request failed.");
				}

				// wait for receiving of the response
				byte[] response = null;
				synchronized (requestLock) {
					try {
						if (timeout > 0) {
							long elapsedTime = MonotonicClock.INSTANCE.currentTimeMillis() - operationStart;
							while ((0 <= elapsedTime) && (elapsedTime < timeout) && (receivedResponse == null)) {
								requestLock.wait(timeout - elapsedTime);
								elapsedTime = MonotonicClock.INSTANCE.currentTimeMillis() - operationStart;
							}
						} else {
							while (receivedResponse == null) {
								requestLock.wait();
							}
						}
					} catch (InterruptedException e) {
						throw new RuntimeException("Request interrupted.", e);
					}

					response = receivedResponse;
					tagOfOpenRequest = -1;
				}

				return response;
			} finally {
				synchronized (requestLock) {
					tagOfOpenRequest = -1;
					receivedResponse = null;
				}
			}
		}
	}

	/**
	 * Handles a received message.
	 * 
	 * @param tag
	 *            the tag associated with the received message.
	 * @param message
	 *            the message content.
	 */
	private void handleMessage(int tag, byte[] message) {
		synchronized (requestLock) {
			if ((tag >= 0) && (tag == tagOfOpenRequest)) {
				receivedResponse = message;
			}

			requestLock.notifyAll();
		}
	}

	/**
	 * Throws an exception, if the register identifier is not valid.
	 * 
	 * @param registerId
	 *            the identifier of a register.
	 */
	private static void checkRegisterId(int registerId) {
		if ((registerId < 0) || (registerId >= 128 * 256)) {
			throw new RuntimeException("Identifier of register (" + registerId + ") is not valid.");
		}
	}

	/**
	 * Throws the runtime exception when the response is not valid, i.e., the
	 * request failed.
	 * 
	 * @param response
	 *            the response to be checked.
	 */
	private static void checkResponse(byte[] response) {
		if (response == null) {
			throw new RuntimeException("No response from registry.");
		}

		if (response[0] != REQUEST_OK_RESPONSE) {
			throw new RuntimeException("Request failed on registry.");
		}
	}

	/**
	 * Builds a request containing a given register id and a given (suffix)
	 * payload.
	 * 
	 * @param requestCode
	 *            the request code.
	 * @param registerId
	 *            the identifier of a register.
	 * @param payload
	 *            the payload to be attached at the end of the request.
	 * @return the request.
	 */
	private static byte[] buildRequest(int requestCode, int registerId, byte[] payload) {
		if (payload == null) {
			payload = EMPTY_PAYLOAD;
		}

		byte[] request;
		if (registerId < 128) {
			request = new byte[2 + payload.length];
			request[1] = (byte) registerId;
			System.arraycopy(payload, 0, request, 2, payload.length);
		} else {
			request = new byte[3 + payload.length];
			request[1] = (byte) ((registerId / 256) | 0x80);
			request[2] = (byte) (registerId % 256);
			System.arraycopy(payload, 0, request, 3, payload.length);
		}
		request[0] = (byte) requestCode;

		return request;
	}

	/**
	 * Encodes a numeric value.
	 * 
	 * @param value
	 *            the value
	 * @return the value encoded as variable-length sequence of bytes.
	 */
	private static byte[] encodeNumber(int value) {
		if (value == Integer.MIN_VALUE) {
			return new byte[] { (byte) 0x40 };
		}

		boolean negativeValue = value < 0;
		value = Math.abs(value);

		// decompose value
		int[] buffer = new int[5];
		int length = 0;
		while (value > 63) {
			buffer[length] = value % 128;
			value = value / 128;
			length++;
		}
		buffer[length] = value;
		length++;

		// update sign bit
		if (negativeValue) {
			buffer[length - 1] = buffer[length - 1] | 0x40;
		}

		// revert bytes and add "next byte" flags
		byte[] result = new byte[length];
		for (int i = length - 1, j = 0; i > 0; i--, j++) {
			result[j] = (byte) (buffer[i] | 0x80);
		}
		result[length - 1] = (byte) buffer[0];

		return result;
	}

	/**
	 * Decodes a numeric value.
	 * 
	 * @param data
	 *            the array of bytes.
	 * @param offset
	 *            the offset in data array where encoded numeric value starts.
	 * @return the decoded value.
	 */
	private static int decodeNumber(byte[] data, int offset) {
		try {
			int aByte = data[offset] & 0xFF;
			boolean negativeValue = ((aByte & 0x40) != 0);
			boolean nextByte = ((aByte & 0x80) != 0);
			int result = (byte) (aByte & 0x3F);

			// special handling for MIN_VALUE
			if (!nextByte && negativeValue && (result == 0)) {
				return Integer.MIN_VALUE;
			}

			// decoding
			while (nextByte) {
				offset++;
				aByte = data[offset] & 0xFF;
				result = result * 128 + (aByte & 0x7F);
				nextByte = ((aByte & 0x80) != 0);
			}

			return negativeValue ? -result : result;
		} catch (Exception e) {
			throw new RuntimeException("Invalid message format.", e);
		}
	}
}
