package com.gboxsw.acpmod.registry;

/**
 * Codec for transforming local hexadecimal strings to remote binary data and
 * vice versa.
 */
public class HexBinaryCodec implements Codec.BinaryCodec {

	/**
	 * Minimal length of binary sequence in bytes.
	 */
	private final int minLength;

	/**
	 * Maximal length of binary sequence in bytes.
	 */
	private final int maxLength;

	/**
	 * Indicates whether spaces are used as delimiter of bytes.
	 */
	private final boolean spaces;

	/**
	 * Constructs a codec for reading and writing binary values encoded as hex
	 * sequences.
	 * 
	 * @param minLength
	 *            the minimal length of binary block to be decoded.
	 * @param maxLength
	 *            the maximal length of binary block to be decoded.
	 * @param spaces
	 *            true, if space is used as delimiter of bytes, false otherwise.
	 */
	public HexBinaryCodec(int minLength, int maxLength, boolean spaces) {
		this.minLength = minLength;
		this.maxLength = maxLength;
		this.spaces = spaces;
	}

	@Override
	public Object decodeRemoteBinaryValue(byte[] remoteValue) {
		if (remoteValue == null) {
			return null;
		}

		if ((remoteValue.length < minLength) || (remoteValue.length > maxLength)) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < remoteValue.length; i++) {
			if (spaces && (i != 0)) {
				sb.append(' ');
			}
			int b = remoteValue[i] & 0xFF;
			if (b < 16) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(b));
		}
		return sb.toString();
	}

	@Override
	public byte[] encodeToBinaryValue(Object localValue) {
		if (!(localValue instanceof String)) {
			return null;
		}

		String binaryString = (String) localValue;
		binaryString = binaryString.trim().replace(" ", "");
		if (binaryString.length() % 2 != 0) {
			return null;
		}

		int dataLength = binaryString.length() / 2;
		if ((dataLength < minLength) || (dataLength > maxLength)) {
			return null;
		}

		byte[] result = new byte[binaryString.length() / 2];
		for (int i = 0; i < result.length; i++) {
			result[i] = (byte) Integer.parseInt(binaryString.substring(2 * i, 2 * i + 2), 16);
		}

		return result;
	}

	public int getMinLength() {
		return minLength;
	}

	public int getMaxLength() {
		return maxLength;
	}

	@Override
	public Class<?> getValueType() {
		return String.class;
	}
}
