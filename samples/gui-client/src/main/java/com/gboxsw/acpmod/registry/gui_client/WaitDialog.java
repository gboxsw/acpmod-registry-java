package com.gboxsw.acpmod.registry.gui_client;

import java.awt.*;

import javax.swing.*;

import javax.swing.border.EmptyBorder;
import net.miginfocom.swing.MigLayout;

/**
 * Wait dialog displayed during network requests or other long-running
 * operations.
 */
@SuppressWarnings("serial")
class WaitDialog extends JDialog {

	/**
	 * Worker executing the long running code.
	 */
	private SwingWorker<?, Void> worker;

	/**
	 * The displayed text.
	 */
	private JLabel lblActionTitle;

	/**
	 * Creates the dialog over a frame.
	 */
	public WaitDialog(JFrame owner) {
		super(owner, true);
		initializeComponents();
	}

	/**
	 * Creates the dialog over a dialog.
	 */
	public WaitDialog(JDialog owner) {
		super(owner, true);
		initializeComponents();
	}

	private void initializeComponents() {
		setResizable(false);
		setTitle("Please wait");
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		setBounds(100, 100, 246, 99);
		getContentPane().setLayout(new BorderLayout());
		JPanel contentPanel = new JPanel();
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new MigLayout("", "[220px]", "[14px][14px]"));
		{
			lblActionTitle = new JLabel("Action");
			contentPanel.add(lblActionTitle, "cell 0 0,alignx center,aligny center");
		}
		{
			JProgressBar progressBar = new JProgressBar();
			progressBar.setIndeterminate(true);
			contentPanel.add(progressBar, "cell 0 1,growx,aligny center");
		}
	}

	/**
	 * Executes a runnable in a separated thread and shows wait dialog during
	 * execution.
	 * 
	 * @param owner
	 *            the frame that will be owner of the dialog window.
	 * @param task
	 *            the runnable to be executed.
	 * @return the value returned by the task.
	 */
	public static void execute(Window owner, String message, long popupAfter, final Runnable task) {
		if (!((owner instanceof JDialog) || (owner instanceof JFrame) || (owner == null))) {
			throw new RuntimeException("Owner of a WaitDialog can be JDialog or JFrame");
		}

		// create dialog
		final WaitDialog dialog = (owner instanceof JDialog) ? new WaitDialog((JDialog) owner)
				: new WaitDialog((JFrame) owner);
		dialog.lblActionTitle.setText(message);

		// execute the code
		dialog.worker = new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				task.run();
				return null;
			}

			@Override
			protected void done() {
				dialog.setVisible(false);
				dialog.dispose();
			}
		};

		dialog.worker.execute();

		// if the dialog is shown after given time period
		if (popupAfter > 0) {
			long maxWait = System.currentTimeMillis() + popupAfter;
			while (!dialog.worker.isDone() && (System.currentTimeMillis() < maxWait)) {
				try {
					Thread.sleep(50);
				} catch (Exception ignore) {
					break;
				}
			}
		}

		// if the runnable is already executed, we don't need to show the dialog
		if (!dialog.worker.isDone()) {
			dialog.setLocationRelativeTo(owner);
			dialog.setVisible(true);
		}

		// wait for completing the runnable
		try {
			dialog.worker.get();
		} catch (Exception e) {
			throw new RuntimeException("Asynchronous operation failed.", e);
		}
	}
}
