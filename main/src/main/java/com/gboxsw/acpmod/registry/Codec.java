package com.gboxsw.acpmod.registry;

/**
 * Value codec for transforming values from remote registers to values of local
 * register and vice versa.
 */
public interface Codec {
	/**
	 * Returns type of local values accepted and produced by the codec.
	 * 
	 * @return the type .
	 */
	public Class<?> getValueType();

	/**
	 * Codec operating on binary values of remote registers.
	 */
	public interface BinaryCodec extends Codec {
		/**
		 * Decodes a remote binary value to a local value.
		 * 
		 * @param remoteValue
		 *            the remote value.
		 * @return the local value. The value null is not allowed as a return
		 *         value.
		 */
		public Object decodeRemoteBinaryValue(byte[] remoteValue);

		/**
		 * Encodes a local value to a remote binary value.
		 * 
		 * @param localValue
		 *            the local value.
		 * @return the remote value.
		 */
		public byte[] encodeToBinaryValue(Object localValue);
	}

	/**
	 * Codec operating on integer values of remote registers.
	 */
	public interface IntCodec extends Codec {
		/**
		 * Decodes a remote integer value to a local value.
		 * 
		 * @param remoteValue
		 *            the remote value.
		 * @return the local value. The value null is not allowed as a return
		 *         value.
		 */
		public Object decodeRemoteIntValue(int remoteValue);

		/**
		 * Encodes a local value to a remote integer value.
		 * 
		 * @param localValue
		 *            the local value.
		 * @return the remote value.
		 */
		public int encodeToIntValue(Object localValue);
	}
}
