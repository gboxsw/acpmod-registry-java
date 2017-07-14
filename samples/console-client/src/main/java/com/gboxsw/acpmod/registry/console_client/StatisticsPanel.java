package com.gboxsw.acpmod.registry.console_client;

import com.gboxsw.acpmod.registry.RequestStatistics;
import com.gboxsw.dataterm.*;
import com.googlecode.lanterna.*;
import com.googlecode.lanterna.graphics.*;
import com.googlecode.lanterna.input.*;
import com.googlecode.lanterna.screen.*;

/**
 * Panel that shows communication statistics.
 */
public class StatisticsPanel extends BaseActionPanel {

	/**
	 * Indicates whether the action (show statistics) is completed.
	 */
	private boolean completed = false;

	/**
	 * Constructs the panel that shows communication statistics.
	 * 
	 * @param dataTerminal
	 *            the data terminal.
	 * @param dataItem
	 *            the associated data item.
	 */
	public StatisticsPanel(DataTerminal dataTerminal, BaseItem dataItem) {
		super(dataTerminal, dataItem);
	}

	@Override
	public int getPanelHeight() {
		return 1;
	}

	@Override
	public void refresh(Screen screen, int startRow) {
		TextGraphics textStyle = screen.newTextGraphics();
		textStyle.setForegroundColor(TextColor.ANSI.WHITE);

		RequestStatistics statistics = ((RegisterItem) dataItem).getRegister().getRegisterCollection().getStatistics().createSnapshot();
		long failureRate = (100 * statistics.getFailedRequests()) / statistics.getTotalRequests();
		textStyle.putString(1, startRow, "Statistics | failed: " + statistics.getFailedRequests() + " (" + failureRate
				+ "%) total: " + statistics.getTotalRequests());
	}

	@Override
	public void handleKeyStroke(KeyStroke keyStroke) {
		if (keyStroke.getKeyType() == KeyType.Escape) {
			completed = true;
		}
	}

	@Override
	public boolean actionCompleted() {
		return completed;
	}

	@Override
	public String getStatusText() {
		return "[ESC] Cancel";
	}
}
