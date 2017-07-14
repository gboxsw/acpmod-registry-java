package com.gboxsw.acpmod.registry.console_client;

import com.gboxsw.dataterm.*;
import com.googlecode.lanterna.input.*;

/**
 * Data terminal for displaying/editing register values.
 */
public class ReqisterDataTerminal extends DataTerminal {

	public ReqisterDataTerminal() {
		setStatusText(getStatusText() + " [i] Info");
	}

	@Override
	protected BaseActionPanel onCreateActionPanel(BaseItem dataItem, KeyStroke keyStroke) {
		if ((keyStroke.getKeyType() == KeyType.Character) && (Character.toLowerCase(keyStroke.getCharacter()) == 'i')) {
			return new StatisticsPanel(this, dataItem);
		}

		return super.onCreateActionPanel(dataItem, keyStroke);
	}
}
