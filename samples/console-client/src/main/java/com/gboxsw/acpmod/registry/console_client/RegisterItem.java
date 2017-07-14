package com.gboxsw.acpmod.registry.console_client;

import com.gboxsw.acpmod.registry.*;
import com.gboxsw.acpmod.registry.Register.ChangeListener;
import com.gboxsw.dataterm.*;

/**
 * Wrapper class for a register.
 */
public class RegisterItem extends BaseItem {

	/**
	 * Wrapped register.
	 */
	private final Register register;

	/**
	 * Constructs data item for accessing register values.
	 * 
	 * @param register
	 *            the register.
	 * @param dataTerminal
	 *            the data terminal receiving callback about change of register.
	 */
	public RegisterItem(Register register) {
		this.register = register;
		this.register.setChangeListener(new ChangeListener() {
			@Override
			public void onChange(Register register) {
				fireValueChanged();
			}
		});
	}

	@Override
	public String getLabel() {
		return register.getName();
	}

	@Override
	public boolean isReadOnly() {
		return register.isReadOnly();
	}

	@Override
	public String getValue() {
		Object value = register.getValue();
		if (value == null) {
			return null;
		} else {
			return value.toString();
		}
	}

	@Override
	public void setValue(String newValue) {
		if (Number.class.isAssignableFrom(register.getType())) {
			newValue = newValue.trim();
			register.setValue(Double.parseDouble(newValue));
			return;
		}

		if (Boolean.class.isAssignableFrom(register.getType())) {
			newValue = newValue.trim();
			if ("0".equals(newValue) || "false".equalsIgnoreCase(newValue)) {
				register.setValue(false);
			} else if ("1".equals(newValue) || "true".equalsIgnoreCase(newValue)) {
				register.setValue(true);
			} else {
				throw new RuntimeException("Undefined conversion from string to " + register.getType().getName());
			}
			
			return;
		}

		if (String.class.isAssignableFrom(register.getType())) {
			register.setValue(newValue);
			return;
		}

		throw new RuntimeException("Undefined conversion from string to " + register.getType().getName());
	}

	/**
	 * Returns wrapped register.
	 * 
	 * @return the wrapped register.
	 */
	public Register getRegister() {
		return register;
	}
}
