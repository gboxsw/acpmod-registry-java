package com.gboxsw.acpmod.registry;

/**
 * Codec for transforming local numbers to remote integer values and vice versa.
 */
public class NumberCodec implements Codec.IntCodec {

	/**
	 * Multiplication constant applied to retrieved value: OUT = SCALE*IN +
	 * SHIFT
	 */
	private final double scale;

	/**
	 * Additive constant that is added to multiplied value: OUT = SCALE*IN +
	 * SHIFT
	 */
	private final double shift;

	/**
	 * Number of decimal digits after decimal point (0-4)
	 */
	private final int decimals;

	/**
	 * 10^decimals
	 */
	private final int decimalsPower;

	/**
	 * Type of local values.
	 */
	private final Class<?> valueType;

	/**
	 * Constructs a codec for reading and writing numeric values from/to remote
	 * integer register.
	 * 
	 * @param scale
	 *            the multiplication constant applied to retrieved value: OUT =
	 *            SCALE*IN + SHIFT
	 * @param shift
	 *            the additive constant that is added to multiplied value: OUT =
	 *            SCALE*IN + SHIFT
	 * @param decimals
	 *            the number of decimal digits after decimal point (0-4)
	 */
	public NumberCodec(double scale, double shift, int decimals) {
		this.scale = scale;
		this.decimals = Math.min(Math.max(decimals, 0), 4);
		this.shift = shift;

		int decPow = 1;
		for (int i = 0; i < decimals; i++) {
			decPow *= 10;
		}
		this.decimalsPower = decPow;
		this.valueType = (this.decimals == 0) ? Long.class : Double.class;
	}

	@Override
	public Object decodeRemoteIntValue(int remoteValue) {
		double filteredValue = remoteValue * scale + shift;
		if (decimals != 0) {
			return Double.valueOf(Math.round(filteredValue * decimalsPower) / (double) decimalsPower);
		} else {
			return Long.valueOf((Math.round(filteredValue)));
		}
	}

	@Override
	public int encodeToIntValue(Object localValue) {
		if (!(localValue instanceof Number)) {
			throw new RuntimeException("The argument is not a numeric value.");
		}

		Number lv = (Number) localValue;
		return (int) Math.round((lv.doubleValue() - shift) / scale);
	}

	/**
	 * Returns the scale factor applied to received value.
	 * 
	 * @return the scale factor.
	 */
	public double getScale() {
		return scale;
	}

	/**
	 * Returns the shift applied to scaled received value.
	 * 
	 * @return the shift.
	 */
	public double getShift() {
		return shift;
	}

	/**
	 * Returns the number of decimals places after decimal point.
	 * 
	 * @return the number of decimal places after decimal point.
	 */
	public int getDecimals() {
		return decimals;
	}

	@Override
	public Class<?> getValueType() {
		return valueType;
	}
}
