package com.gboxsw.acpmod.registry;

/**
 * Codec for transforming boolean values to remote integer values and vice
 * versa.
 */
public class BooleanCodec implements Codec.IntCodec {

	/**
	 * Singleton instance of the boolean codec.
	 */
	public static final BooleanCodec INSTANCE = new BooleanCodec();

	@Override
	public Class<?> getValueType() {
		return Boolean.class;
	}

	@Override
	public Object decodeRemoteIntValue(int remoteValue) {
		return (remoteValue > 0) ? Boolean.TRUE : Boolean.FALSE;
	}

	@Override
	public int encodeToIntValue(Object localValue) {
		if (!(localValue instanceof Boolean)) {
			throw new IllegalArgumentException("The argument is not a boolean value.");
		}

		Boolean value = (Boolean) localValue;
		return value.booleanValue() ? 1 : 0;
	}

}
