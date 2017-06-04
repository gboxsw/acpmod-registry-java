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
	 * Constructs a codec for reading and writing binary values encoded as hex
	 * sequences.
	 * 
	 */
	public HexBinaryCodec(int minLength, int maxLength) {
		this.minLength = minLength;
		this.maxLength = maxLength;
	}

	@Override
	public Object decodeRemoteBinaryValue(byte[] remoteValue) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < remoteValue.length; i++) {
			if (i != 0) {
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
