package com.gboxsw.acpmod.registry.console_client;

import java.io.File;
import java.util.*;
import com.gboxsw.acpmod.registry.*;
import com.gboxsw.acpmod.registry.XmlLoader.RegisterCollectionConfig;
import com.gboxsw.dataterm.*;

public class App {

	public static void main(String[] args) {
		// process command line arguments
		if ((args.length == 0) || (args[args.length - 1].startsWith("-"))) {
			System.out.println("Usage: java -jar regdt.jar [OPTION]... GATEWAY_XML_FILE");
			System.out.println("Options:");
			System.out.println("-s\tsort items by name");
			System.out.println("-n\tdisable update/registry hints");
			return;
		}

		// parse switches
		Set<String> switches = new HashSet<>();
		for (String arg : args) {
			if (arg.startsWith("-")) {
				switches.add(arg.substring(1).trim());
			}
		}

		// create data holders
		List<Register> registers = new ArrayList<>();
		Map<String, RegisterCollectionConfig> collections = new HashMap<>();

		// load gateway
		XmlLoader loader = new XmlLoader();
		Gateway gateway = loader.loadGatewayFromXml(new File(args[args.length - 1]), collections, registers);
		gateway.start();

		// Sort registers (if required)
		if (switches.contains("s")) {
			Collections.sort(registers, new Comparator<Register>() {
				@Override
				public int compare(Register o1, Register o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
		}

		// create update controller
		AutoUpdater controller = new AutoUpdater();
		controller.addRegisters(registers);

		// apply properties of register collection
		if (!switches.contains("n")) {
			for (RegisterCollectionConfig collectionConfig : collections.values()) {
				if (collectionConfig.hintSettings != null) {
					controller.useRegistryHints(collectionConfig.registerCollection, collectionConfig.hintSettings);
				}
			}
		}

		// Create and initialize terminal
		DataTerminal dataTerminal = new ReqisterDataTerminal();
		for (Register register : registers) {
			dataTerminal.addDataItem(new RegisterItem(register));
		}

		dataTerminal.setScreenRefreshPeriod(300);
		dataTerminal.launch();

		controller.removeAllRegisters();
		gateway.stop();
	}
}
