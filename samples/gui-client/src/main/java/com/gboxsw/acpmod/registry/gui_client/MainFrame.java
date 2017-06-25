package com.gboxsw.acpmod.registry.gui_client;

import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import com.gboxsw.acpmod.registry.*;
import java.util.*;

import java.awt.BorderLayout;
import java.awt.Point;

import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

import com.gboxsw.acpmod.registry.XmlLoader.RegisterCollectionConfig;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
class MainFrame extends JFrame {

	/**
	 * Model for table with current values of registers.
	 */
	private static class RegistryTableModel extends AbstractTableModel implements Register.ChangeListener {
		/**
		 * List of registers displayed in the table.
		 */
		private final List<Register> registers = new ArrayList<Register>();

		/**
		 * Reverse indices for displayed registers.
		 */
		private final Map<Register, Integer> registerIndices = new HashMap<Register, Integer>();

		/**
		 * Sets displayed registers.
		 * 
		 * @param registers
		 *            the list of register to be displayed.
		 */
		public void showRegisters(List<Register> registers) {
			this.registers.clear();
			this.registerIndices.clear();

			// add new registers to the list of displayed registers
			if (registers != null) {
				this.registers.addAll(registers);

				for (int i = 0; i < this.registers.size(); i++) {
					registerIndices.put(this.registers.get(i), i);
				}

				// associate change listener
				for (Register r : registers) {
					r.setChangeListener(this);
				}
			}

			// fire change of data
			fireTableDataChanged();
		}

		@Override
		public int getColumnCount() {
			return 2;
		}

		@Override
		public int getRowCount() {
			return registers.size();
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			final Register register = registers.get(rowIndex);
			if (register == null) {
				return null;
			}

			switch (columnIndex) {
			case 0:
				return register.getName();
			case 1:
				return register.getValue();
			}

			return null;
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			switch (columnIndex) {
			case 0:
				return String.class;
			case 1:
				return String.class;
			}

			return null;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "Name";
			case 1:
				return "Value";
			}

			return null;
		}

		@Override
		public void onChange(final Register register) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Integer registerIdx = registerIndices.get(register);
					if (registerIdx != null) {
						fireTableRowsUpdated(registerIdx, registerIdx);
					}
				}
			});
		}
	}

	private static final long serialVersionUID = 817711614769157024L;

	/**
	 * The content pane.
	 */
	private JPanel contentPane;

	/**
	 * Table displaying registers.
	 */
	private JTable registersTable;

	/**
	 * File chooser.
	 */
	private JFileChooser fileChooser = new JFileChooser();

	/**
	 * Table model for table with register values.
	 */
	private RegistryTableModel tableModel = new RegistryTableModel();

	/**
	 * Active gateway.
	 */
	private Gateway gateway;

	/**
	 * Indicates whether the gateway is running.
	 */
	private boolean gatewayRunning;

	/**
	 * Active register collections.
	 */
	private final Map<String, RegisterCollectionConfig> registerCollections = new HashMap<>();

	/**
	 * Registers.
	 */
	private final List<Register> registers = new ArrayList<>();

	/**
	 * Managed collection of registers.
	 */
	private final AutoUpdater autoUpdater = new AutoUpdater();

	/**
	 * Combobox with available register collections
	 */
	private JComboBox<String> cmbCollections;

	/**
	 * Button for starting/stopping gateway.
	 */
	private JButton btnStartStop;

	/**
	 * Text field displaying absolute path of file that contains configuration
	 * of the loaded gateway.
	 */
	private JTextField tfGatewayFile;

	/**
	 * Button for loading gateway configuration.
	 */
	private JButton btnLoad;
	private JButton btnFilter;

	/**
	 * Create the frame.
	 */
	public MainFrame() {
		setTitle("Gateway client");
		initializeComponents();

		fileChooser.setFileFilter(new FileNameExtensionFilter("XML file", "xml"));
		fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
	}

	/**
	 * Create and initialize components.
	 */
	private void initializeComponents() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JPanel headPanel = new JPanel();
		contentPane.add(headPanel, BorderLayout.NORTH);
		headPanel.setLayout(new MigLayout("", "[][grow][][]", "[][]"));

		btnLoad = new JButton("Load");
		btnLoad.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				loadGatewayFromXml();
			}
		});

		tfGatewayFile = new JTextField();
		tfGatewayFile.setEditable(false);
		headPanel.add(tfGatewayFile, "cell 0 0 2 1,growx");
		tfGatewayFile.setColumns(10);
		headPanel.add(btnLoad, "cell 2 0");

		btnStartStop = new JButton("Start");
		btnStartStop.setEnabled(false);
		btnStartStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (gatewayRunning) {
					stopClicked();
				} else {
					startClicked();
				}
			}
		});
		headPanel.add(btnStartStop, "cell 3 0");

		JLabel lblRegisterCollection = new JLabel("Register collection:");
		headPanel.add(lblRegisterCollection, "cell 0 1,alignx left");

		cmbCollections = new JComboBox<String>();
		headPanel.add(cmbCollections, "cell 1 1 2 1,growx");

		btnFilter = new JButton("Filter");
		btnFilter.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				filterClicked();
			}
		});
		btnFilter.setEnabled(false);
		headPanel.add(btnFilter, "cell 3 1");

		JScrollPane scrollPaneForTable = new JScrollPane();
		contentPane.add(scrollPaneForTable, BorderLayout.CENTER);

		registersTable = new JTable();
		registersTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent args) {
				Point p = args.getPoint();
				int row = registersTable.rowAtPoint(p);
				if ((row >= 0) && (args.getClickCount() == 2) && gatewayRunning) {
					handleRegisterDoubleClick(registersTable.convertRowIndexToModel(row));
				}
			}
		});
		registersTable.setAutoCreateRowSorter(true);
		registersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		registersTable.setModel(tableModel);
		scrollPaneForTable.setViewportView(registersTable);
	}

	/**
	 * Loads gateway configuration from file.
	 */
	private void loadGatewayFromXml() {
		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		XmlLoader registryLoader = new XmlLoader();

		Map<String, RegisterCollectionConfig> loadedRegisterCollections = new HashMap<>();
		List<Register> loadedRegisters = new ArrayList<>();
		Gateway loadedGateway = null;

		try {
			loadedGateway = registryLoader.loadGatewayFromXml(fileChooser.getSelectedFile(), loadedRegisterCollections,
					loadedRegisters);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Loading of gateway configuration failed.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		gateway = null;
		registerCollections.clear();
		registers.clear();

		gateway = loadedGateway;
		registerCollections.putAll(loadedRegisterCollections);
		registers.addAll(loadedRegisters);

		tfGatewayFile.setText(fileChooser.getSelectedFile().getAbsolutePath().toString());

		String[] registerCollectionNames = new String[registerCollections.size() + 1];
		registerCollectionNames = registerCollections.keySet().toArray(registerCollectionNames);
		registerCollectionNames[registerCollectionNames.length - 1] = "";
		Arrays.sort(registerCollectionNames);

		cmbCollections.setModel(new DefaultComboBoxModel<>(registerCollectionNames));
		tableModel.showRegisters(registers);

		btnStartStop.setEnabled(true);
		btnFilter.setEnabled(true);
	}

	/**
	 * Invoked when button for starting the gateway is clicked.
	 */
	private void startClicked() {
		WaitDialog.execute(this, "Starting...", 300, new Runnable() {

			@Override
			public void run() {
				gateway.start();
			}
		});

		btnStartStop.setText("Stop");
		btnLoad.setEnabled(false);

		autoUpdater.addRegisters(registers);
		for (RegisterCollectionConfig cfg : registerCollections.values()) {
			autoUpdater.useRegistryHints(cfg.registerCollection, cfg.hintIntervalInMillis, cfg.timeoutInMillis);
		}

		gatewayRunning = true;
	}

	private void stopClicked() {
		WaitDialog.execute(this, "Stopping...", 300, new Runnable() {
			@Override
			public void run() {
				gateway.stop();
			}
		});

		btnStartStop.setText("Start");
		btnLoad.setEnabled(true);

		autoUpdater.removeAllRegisters();
		for (RegisterCollectionConfig cfg : registerCollections.values()) {
			autoUpdater.disableRegistryHints(cfg.registerCollection);
		}

		gatewayRunning = false;
	}

	/**
	 * Handles double click on record (row) of a register.
	 * 
	 * @param index
	 *            the index of selected (double-clicked) register.
	 */
	private void handleRegisterDoubleClick(int index) {
		final Register register = tableModel.registers.get(index);
		if (!register.isReadOnly()) {
			String newValueString = JOptionPane.showInputDialog(this, register.getName(), register.getValue());
			if (newValueString != null) {
				Object newValue = null;

				if (Number.class.isAssignableFrom(register.getType())) {
					try {
						newValue = Double.parseDouble(newValueString);
					} catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(this, "Numeric value is expected.", "Error",
								JOptionPane.ERROR_MESSAGE);
						return;
					}
				}

				if (Boolean.class.isAssignableFrom(register.getType())) {
					newValueString = newValueString.trim().toLowerCase();
					if ("0".equals(newValueString) || "false".equals(newValueString)) {
						newValue = false;
					} else if ("1".equals(newValueString) || "true".equals(newValueString)) {
						newValue = true;
					} else {
						JOptionPane.showMessageDialog(this, "Boolean value (true/false/1/0) is expected.", "Error",
								JOptionPane.ERROR_MESSAGE);
						return;
					}
				}

				if (String.class.isAssignableFrom(register.getType())) {
					newValue = newValueString;
				}

				if (newValue == null) {
					JOptionPane.showMessageDialog(this, "Value cannot be converted to type compatible with register.",
							"Error", JOptionPane.ERROR_MESSAGE);
					return;
				}

				try {
					final Object requestedNewValue = newValue;
					WaitDialog.execute(this, "Changing...", 300, new Runnable() {
						@Override
						public void run() {
							register.setValue(requestedNewValue);
						}
					});
				} catch (Exception e) {
					JOptionPane.showMessageDialog(this, "Request to change value of the register failed.", "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}

	/**
	 * Invoked when filter button is clicked.
	 */
	private void filterClicked() {
		if (cmbCollections.getSelectedIndex() == 0) {
			tableModel.showRegisters(registers);
			return;
		}

		RegisterCollectionConfig selectedCollectionConfig = registerCollections
				.get(cmbCollections.getSelectedItem().toString());

		List<Register> filteredRegisters = new ArrayList<>();
		for (Register register : registers) {
			if (register.getRegisterCollection() == selectedCollectionConfig.registerCollection) {
				filteredRegisters.add(register);
			}
		}

		tableModel.showRegisters(filteredRegisters);
	}
}
