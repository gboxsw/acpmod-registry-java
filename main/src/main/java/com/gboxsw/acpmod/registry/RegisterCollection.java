package com.gboxsw.acpmod.registry;

/**
 * Interface to access a remote collection (group) of registers.
 */
public interface RegisterCollection {

	/**
	 * Returns the gateway that provides access to the register collection.
	 * 
	 * @return the gateway.
	 */
	public Gateway getGateway();

	/**
	 * Reads a change hint from register collection and eventually notifies that
	 * the client is aware of change of a register. Hint is the identifier of a
	 * register whose value has been changed but not read.
	 * 
	 * @param confirmedRegisterId
	 *            the identifier of register that was not read, however the
	 *            client confirms that it is aware of change of this register.
	 *            If the value is negative, no register will be confirmed.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            read operation. Negative value or zero mean that there is no
	 *            timeout for completing the operation.
	 * @return the id of changed register or negative value, if no register is
	 *         marked as changed and unread.
	 */
	public int getChangeHintId(int confirmedRegisterId, long timeout);

	/**
	 * Reads a value from an integer register.
	 * 
	 * @param registerId
	 *            the identifier of the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            read operation. Negative value or zero mean that there is no
	 *            timeout for completing the operation.
	 * @return the value of register
	 * @throws RuntimeException
	 *             if the operation failed.
	 */
	public int readIntegerRegister(int registerId, long timeout) throws RuntimeException;

	/**
	 * Writes a value to an integer register.
	 * 
	 * @param registerId
	 *            the identifier of the register.
	 * @param value
	 *            the value to be written to the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            write operation. Negative value or zero mean that there is no
	 *            timeout for completing the operation.
	 * @throws RuntimeException
	 *             if the operation failed.
	 */
	public void writeIntegerRegister(int registerId, int value, long timeout) throws RuntimeException;

	/**
	 * Reads a value from a binary register.
	 * 
	 * @param registerId
	 *            the identifier of the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            read operation. Negative value or zero mean that there is no
	 *            timeout for completing the operation.
	 * @return the value of register
	 * @throws RuntimeException
	 *             if the operation failed.
	 */
	public byte[] readBinaryRegister(int registerId, long timeout) throws RuntimeException;

	/**
	 * Writes a value to a binary register.
	 * 
	 * @param registerId
	 *            the identifier of the register.
	 * @param value
	 *            the value to be written to the register.
	 * @param timeout
	 *            the maximal amount of time in milliseconds to complete the
	 *            write operation. Negative value or zero mean that there is no
	 *            timeout for completing the operation.
	 * @throws RuntimeException
	 *             if the operation failed.
	 */
	public void writeBinaryRegister(int registerId, byte[] value, long timeout) throws RuntimeException;

	/**
	 * Returns statistics of requests.
	 * 
	 * @return the statistics of requests.
	 */
	public RequestStatistics getStatistics();
}
