package com.gboxsw.acpmod.registry;

import java.io.File;
import java.util.*;
import org.w3c.dom.*;

import com.gboxsw.acpmod.gep.SerialPortSocket;
import com.gboxsw.acpmod.gep.TCPSocket;
import com.gboxsw.acpmod.registry.Register.ConnectionSettings;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Loader of gateways and registers from an xml file.
 */
public class XmlLoader {

	/**
	 * Codec factory.
	 */
	public interface CodecFactory {
		/**
		 * Creates new instance of value codec with respect to provided
		 * configuration properties. Default values are applied, if necessary.
		 * 
		 * @param properties
		 *            the configuration properties.
		 * @return new codec.
		 */
		public Codec newCodec(Map<String, String> properties);
	}

	/**
	 * Gateway and register collection factory.
	 */
	public interface GatewayFactory {

		/**
		 * Creates new instance of gateway with respect to given settings.
		 * 
		 * @param settings
		 *            the xml element with connection settings.
		 * @return the gateway to remote registers organized in collections.
		 */
		public Gateway createGateway(Element settings);

		/**
		 * Creates new instance of register collection using a gateway created
		 * and configured by this factory.
		 * 
		 * @param gateway
		 *            the gateway providing access to register collection which
		 *            was created by this factory.
		 * @param properties
		 *            the configuration properties.
		 * @return the register collection.
		 */
		public RegisterCollection createRegisterCollection(Gateway gateway, Map<String, String> properties);
	}

	/**
	 * RegisterCollection and its configuration loaded from an xml configuration
	 * file.
	 */
	public static class RegisterCollectionConfig {

		/**
		 * The register collection.
		 */
		public RegisterCollection registerCollection;

		/**
		 * Timeout in milliseconds for any communication operation. Zero or
		 * negative value indicate use of default value.
		 */
		public long timeoutInMillis;

		/**
		 * Time in milliseconds after which the last hints is expired and should
		 * be updated. Zero or negative value indicate that usage of hints is
		 * disabled.
		 */
		public long hintIntervalInMillis;

		/**
		 * Map with all properties specified for given collection of registers.
		 */
		public final Map<String, String> properties = new HashMap<>();
	}

	/**
	 * Default codec used when no codec is provided in xml description.
	 */
	private Codec defaultCodec = new NumberCodec(1, 0, 0);

	/**
	 * Mapping of codec names to codec factories.
	 */
	public final Map<String, CodecFactory> codecFactories = new HashMap<>();

	/**
	 * Mapping of name of gateway types to gateway factories.
	 */
	public final Map<String, GatewayFactory> gatewayFactories = new HashMap<>();

	/**
	 * Constructs register loader with default codec factories.
	 */
	public XmlLoader() {
		// default codec factory for numbers
		codecFactories.put("number", new CodecFactory() {
			@Override
			public Codec newCodec(Map<String, String> properties) {
				// complete properties
				Map<String, String> completeProperties = new HashMap<>();
				completeProperties.put("scale", "1");
				completeProperties.put("shift", "0");
				completeProperties.put("decimals", "0");
				if (properties != null) {
					completeProperties.putAll(properties);
				}

				try {
					// create instance
					return new NumberCodec(Double.parseDouble(completeProperties.get("scale")),
							Double.parseDouble(completeProperties.get("shift")),
							Integer.parseInt(completeProperties.get("decimals")));
				} catch (Exception e) {
					throw new RuntimeException("Construction of value codec with respect to given properties failed.",
							e);
				}
			}
		});

		// default codec factory for numbers
		codecFactories.put("binary", new CodecFactory() {
			@Override
			public Codec newCodec(Map<String, String> properties) {
				// complete properties
				Map<String, String> completeProperties = new HashMap<>();
				completeProperties.put("minlength", "0");
				completeProperties.put("maxlength", "1024");
				if (properties != null) {
					completeProperties.putAll(properties);
				}

				try {
					// create instance
					return new HexBinaryCodec(Integer.parseInt(completeProperties.get("minlength")),
							Integer.parseInt(completeProperties.get("maxlength")));
				} catch (Exception e) {
					throw new RuntimeException("Construction of value codec with respect to given properties failed.",
							e);
				}
			}
		});

		// default gateway factory for GEP protocol over serial port.
		gatewayFactories.put("gep-serial", new GatewayFactory() {

			@Override
			public RegisterCollection createRegisterCollection(Gateway gateway, Map<String, String> properties) {
				int deviceId;
				try {
					deviceId = Integer.parseInt(properties.get("gepid"));
				} catch (Exception e) {
					throw new RuntimeException(
							"Invalid value of attribute \"gepid\" identifying device in GEP network.");
				}
				return ((GepGateway) gateway).getRegisterCollection(deviceId);
			}

			@Override
			public Gateway createGateway(Element settings) {
				String port;
				int baudRate, gepId;
				try {
					port = settings.getElementsByTagName("port").item(0).getTextContent();
					baudRate = Integer.parseInt(settings.getElementsByTagName("baudrate").item(0).getTextContent());
					gepId = Integer.parseInt(settings.getElementsByTagName("gepid").item(0).getTextContent());
				} catch (Exception e) {
					throw new RuntimeException(
							"Invalid or missing settings (port, baudrate, gepid) for gateway using a serial port.", e);
				}

				return new GepGateway(new SerialPortSocket(port, baudRate), gepId, true);
			}
		});

		// default gateway factory for GEP protocol over TCP.
		gatewayFactories.put("gep-tcp", new GatewayFactory() {

			@Override
			public RegisterCollection createRegisterCollection(Gateway gateway, Map<String, String> properties) {
				int deviceId;
				try {
					deviceId = Integer.parseInt(properties.get("gepid"));
				} catch (Exception e) {
					throw new RuntimeException(
							"Invalid value of attribute \"gepid\" identifying device in GEP network.");
				}
				return ((GepGateway) gateway).getRegisterCollection(deviceId);
			}

			@Override
			public Gateway createGateway(Element settings) {
				String host;
				int port, gepId;
				try {
					host = settings.getElementsByTagName("host").item(0).getTextContent();
					port = Integer.parseInt(settings.getElementsByTagName("port").item(0).getTextContent());
					gepId = Integer.parseInt(settings.getElementsByTagName("gepid").item(0).getTextContent());
				} catch (Exception e) {
					throw new RuntimeException(
							"Invalid or missing settings (host, port, gepid) for gateway using a tcp connection.", e);
				}

				return new GepGateway(new TCPSocket(host, port), gepId, true);
			}
		});
	}

	/**
	 * Returns the default codec used when no codec is provided.
	 * 
	 * @return the default codec.
	 */
	public Codec getDefaultCodec() {
		return defaultCodec;
	}

	/**
	 * Sets the default codec.
	 * 
	 * @param defaultCodec
	 *            the codec.
	 */
	public void setDefaultCodec(Codec defaultCodec) {
		if (defaultCodec == null) {
			throw new NullPointerException("Default codec cannot be null.");
		}

		this.defaultCodec = defaultCodec;
	}

	/**
	 * Returns the registered codec factories.
	 * 
	 * @return the mapping of codec types to coding factories.
	 */
	public Map<String, CodecFactory> getCodecFactories() {
		return codecFactories;
	}

	/**
	 * Returns the registered gateway factories.
	 * 
	 * @return the mapping of gateway types to gateway factories.
	 */
	public Map<String, GatewayFactory> getGatewayFactories() {
		return gatewayFactories;
	}

	/**
	 * Creates gateway to remote registers.
	 * 
	 * @param xmlFile
	 *            the xml file with configuration of registers.
	 * @param collections
	 *            the map where created collections of registers are stored.
	 * @param registers
	 *            the list where created registers are stored.
	 * @return the created gateway.
	 */
	public Gateway loadGatewayFromXml(File xmlFile, Map<String, RegisterCollectionConfig> collections,
			List<Register> registers) {
		try {
			// parse xml file
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
			Document doc = parser.parse(xmlFile);
			Element root = doc.getDocumentElement();
			if (!"gateway".equals(root.getNodeName())) {
				throw new RuntimeException("Invalid document root (\"gateway\" expected).");
			}

			// create appropriate gateway factory.
			GatewayFactory gatewayFactory = gatewayFactories.get(root.getAttribute("type").trim());
			if (gatewayFactory == null) {
				throw new RuntimeException(
						"No factory defined for gateway type \"" + root.getAttribute("type").trim() + "\".");
			}

			// find settings element
			Element settingsElement = null;
			NodeList children = root.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if ((child instanceof Element) && ("settings".equals(child.getNodeName()))) {
					settingsElement = (Element) child;
					break;
				}
			}

			// create gateway
			Gateway gateway = gatewayFactory.createGateway(settingsElement);

			// load collections
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (!(child instanceof Element) || (!"collection".equals(child.getNodeName()))) {
					continue;
				}

				// process a collection element
				Element collectionElement = (Element) child;
				String collectionId = collectionElement.getAttribute("id").trim();

				// load collection properties (element attributes)
				Map<String, String> collectionProperties = new HashMap<>();
				NamedNodeMap attributes = collectionElement.getAttributes();
				if (attributes != null) {
					for (int j = 0; j < attributes.getLength(); j++) {
						collectionProperties.put(attributes.item(j).getNodeName(), attributes.item(j).getNodeValue());
					}
				}

				// create register collection and put it into the output map
				RegisterCollection registerCollection = gatewayFactory.createRegisterCollection(gateway,
						collectionProperties);
				if ((collections != null) && (!collectionId.isEmpty())) {
					RegisterCollectionConfig info = new RegisterCollectionConfig();
					info.registerCollection = registerCollection;
					if (collectionProperties.containsKey("hints")) {
						info.hintIntervalInMillis = Long.parseLong(collectionProperties.get("hints"));
					}
					if (collectionProperties.containsKey("timeout")) {
						info.timeoutInMillis = Long.parseLong(collectionProperties.get("timeout"));
					}
					info.properties.putAll(collectionProperties);

					if (collections.containsKey(collectionId)) {
						throw new RuntimeException("Duplicated identifer of collection: " + collectionId);
					}

					collections.put(collectionId, info);
				}

				// load registers of collection and put them into the output
				// list
				List<Register> createdRegisters = loadRegistersFromXml(collectionElement, registerCollection,
						xmlFile.getAbsoluteFile().getParentFile());

				// apply modifications of connection settings defined in the xml
				// file.
				applyConnectionSettings(collectionProperties, createdRegisters);

				if (registers != null) {
					registers.addAll(createdRegisters);
				}
			}

			return gateway;
		} catch (Exception e) {
			throw new RuntimeException("Construction of gateway according to the XML file (" + xmlFile + ") failed.",
					e);
		}
	}

	/**
	 * Applies modifications of default connection settings to a given list of
	 * registers.
	 * 
	 * @param collectionProperties
	 *            the map with configuration of connection settings.
	 * @param registers
	 *            the list of registers.
	 */
	private void applyConnectionSettings(Map<String, String> collectionProperties, List<Register> registers) {
		ConnectionSettings cs = Register.DEFAULT_CONNECTION_SETTINGS;
		if (collectionProperties.containsKey("timeout")) {
			long timeout = Long.parseLong(collectionProperties.get("timeout"));
			if (timeout > 0) {
				cs = cs.withTimeout(timeout);
			}
		}

		for (Register register : registers) {
			register.setConnectionSettings(cs);
		}
	}

	/**
	 * Creates registers according to definitions of registers in an xml
	 * element.
	 * 
	 * @param xmlRegisters
	 *            the xml element containing child elements with register
	 *            definitions.
	 * @param registerCollection
	 *            the remote collection of registers that provides access to
	 *            remote registers.
	 * @param path
	 *            the current path for loading registers from external files.
	 * @return list of created registers.
	 */
	private List<Register> loadRegistersFromXml(Element xmlRegisters, RegisterCollection registerCollection,
			File path) {
		List<Register> createdRegisters = new ArrayList<Register>();
		NodeList children = xmlRegisters.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (!(child instanceof Element)) {
				continue;
			}

			Element childElement = (Element) child;
			switch (child.getNodeName()) {
			// register
			case "register": {
				createdRegisters.add(createRegisterFromXml(childElement, registerCollection));
				break;
			}
			// include
			case "include": {
				File externalFile = new File(childElement.getTextContent().trim());
				if (!externalFile.isAbsolute()) {
					if (path == null) {
						throw new RuntimeException("Include of external xml file failed due to missing current path.");
					}

					externalFile = new File(path, externalFile.toString());
				}

				// create registers according to included file
				List<Register> includedRegisters = loadRegistersFromXml(externalFile, registerCollection);

				// if defined, add a prefix to register names of included
				// registers
				if (childElement.hasAttribute("prefix")) {
					String namePrefix = childElement.getAttribute("prefix").trim();
					for (Register register : includedRegisters) {
						register.setName(namePrefix + register.getName());
					}
				}

				createdRegisters.addAll(includedRegisters);
				break;
			}
			}
		}

		return createdRegisters;
	}

	/**
	 * Creates registers according to an xml file.
	 * 
	 * @param xmlFile
	 *            the xml file with configuration of registers.
	 * @param registerCollection
	 *            the remote collection of registers that provides access to
	 *            remote registers.
	 * @return the list with created registers.
	 */
	public List<Register> loadRegistersFromXml(File xmlFile, RegisterCollection registerCollection) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
			Document doc = parser.parse(xmlFile);
			Element root = doc.getDocumentElement();
			if (!"registers".equals(root.getNodeName())) {
				throw new RuntimeException("Invalid document root (\"registers\" expected).");
			}
			return loadRegistersFromXml(root, registerCollection, xmlFile.getAbsoluteFile().getParentFile());
		} catch (Exception e) {
			throw new RuntimeException("Loading of the XML file (" + xmlFile + ") failed.", e);
		}
	}

	/**
	 * Creates a new register from xml configuration.
	 * 
	 * @param xmlConfiguration
	 *            the xml element with register configuration.
	 * @param registerCollection
	 *            the remote collection of registers that provides access to the
	 *            remote register.
	 * @return the created register.
	 */
	private Register createRegisterFromXml(Element xmlConfiguration, RegisterCollection registerCollection) {
		// mandatory attribute ID
		int id = Integer.parseInt(xmlConfiguration.getAttribute("id"));
		// optional attribute read-only, default false
		boolean readOnly = "true".equals(xmlConfiguration.getAttribute("read-only"));

		// read name, description, and codec (if provided)
		String name = null;
		String description = null;
		Element codecXmlElement = null;
		NodeList children = xmlConfiguration.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element) {
				switch (((Element) child).getNodeName()) {
				case "name":
					name = child.getTextContent().trim();
					break;
				case "description":
					description = child.getTextContent().trim();
					break;
				case "codec":
					codecXmlElement = (Element) child;
				}
			}
		}

		Codec codec;

		// use default codec, if codec is not provided by the register
		// description
		if (codecXmlElement == null) {
			codec = defaultCodec;
		} else {
			try {
				codec = createCodecFromXml(codecXmlElement);
			} catch (Exception e) {
				throw new RuntimeException("Construction of register \"" + name + "\" failed.", e);
			}
		}

		// create register
		Register result = new Register(registerCollection, id, readOnly, codec);

		// set name and description
		result.setName(name);
		result.setDescription(description);

		// optional attribute update period in milliseconds, default 1000 (1
		// second)
		try {
			String updateInterval = xmlConfiguration.getAttribute("update-interval").trim();
			long multiplicator = 1;
			if (updateInterval.endsWith("s")) {
				multiplicator = 1000;
				updateInterval = updateInterval.substring(0, updateInterval.length() - 1).trim();
			}
			result.setUpdateInterval(Math.round(Double.parseDouble(updateInterval) * multiplicator));
		} catch (Exception ignore) {

		}

		return result;
	}

	/**
	 * Creates a new register codec from an xml configuration.
	 * 
	 * @param xmlCodec
	 *            the xml element with codec specification.
	 * @return the created codec
	 * @throws RuntimeException
	 *             when construction of codec failed.
	 */
	private Codec createCodecFromXml(Element xmlCodec) throws RuntimeException {
		// read type of codec
		String type = xmlCodec.getAttribute("type").trim();

		// read properties
		Map<String, String> properties = new HashMap<>();
		NodeList children = xmlCodec.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element) {
				properties.put(child.getNodeName(), child.getTextContent().trim());
			}
		}

		// create codec using a factory
		CodecFactory codecFactory = codecFactories.get(type);
		if (codecFactory == null) {
			throw new RuntimeException("Unknown codec type: \"" + type + "\".");
		}

		return codecFactory.newCodec(properties);
	}

}
