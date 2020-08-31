package com.cleo.services.harmony;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.cleo.services.harmony.pojo.AS2;
import com.cleo.services.harmony.pojo.ActionPOJO;
import com.cleo.services.harmony.pojo.SFTP;
import com.cleo.services.harmony.pojo.FTP;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.LinkedTreeMap;
import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.exceptions.CsvException;


public class ConvertCSVToHarmonyJSON {

	public static String actionSeparatorRegex = "[\\|;]";

	public static Gson gson = new Gson();

	public static enum  Types {
		AS2,
		SFTP,
		FTP
	}
	
	public static void main(String[] args) throws IOException, CsvException {
		new ApplicationProperties().readProperties();
		String mailboxFilename = ApplicationProperties.appProps.getProperty("mailBoxFile");
		System.out.println("Finished reading the app config file...");
		
        JsonArray jsonArr = new JsonArray();

        if (ApplicationProperties.appProps.contains("groupFile")) {
			CSVReader grpReader = new CSVReader(new FileReader(ApplicationProperties.appProps.getProperty("groupFile")));
			List<String[]> grpElements = grpReader.readAll();
			for (String[] line : grpElements) {
				if (!line[0].equals("UserAlias"))
					jsonArr.add(contructUsrGrpJSON(line));
			}
		}

		List<String> lines = Files.readAllLines(Paths.get(mailboxFilename));
		if (lines.size() > 0 && (lines.get(0).contains("type") || lines.get(0).contains("Type"))) {
			String type = getFileType(mailboxFilename);
			if (type.equalsIgnoreCase(Types.AS2.name())) {
				List<JsonElement> as2Jsons = createAS2Hosts(mailboxFilename);
				for (JsonElement as2Host : as2Jsons) {
					jsonArr.add(as2Host);
				}
			} else if (type.equalsIgnoreCase(Types.SFTP.name())) {
				List<JsonElement> ftpJsons = createSFTPHosts(mailboxFilename);
				for (JsonElement ftpHost : ftpJsons) {
					jsonArr.add(ftpHost);
				}
			} else if (type.equalsIgnoreCase(Types.FTP.name())) {
				List<JsonElement> ftpJsons = createFTPHosts(mailboxFilename);
				for (JsonElement ftpHost : ftpJsons) {
					jsonArr.add(ftpHost);
				}
			} else {
				System.out.println("Invalid type specified: " + type);
			}
		} else {
			HeaderColumnNameMappingStrategy mailboxStrategy = new HeaderColumnNameMappingStrategy<>();
			mailboxStrategy.setType(MailboxCSV.class);
			CSVReader reader2 = new CSVReader(new FileReader(mailboxFilename));
			CsvToBean csvToBean = new CsvToBean();
			csvToBean.setCsvReader(reader2);
			csvToBean.setMappingStrategy(mailboxStrategy);
			List<MailboxCSV> mailboxCSVList = csvToBean.parse();

			for (MailboxCSV mailboxCSV : mailboxCSVList) {
				jsonArr.add(contructMailboxJSON(mailboxCSV));
			}
		}
        
        FileWriter writer = new FileWriter(new File(ApplicationProperties.appProps.getProperty("jsonFile")));
        gson.toJson(jsonArr, writer);
        writer.close();
	}

	private static String getFileType(String filename) throws FileNotFoundException {
		HeaderColumnNameMappingStrategy mailboxStrategy = new HeaderColumnNameMappingStrategy<>();
		mailboxStrategy.setType(ClientCSV.class);
		CSVReader reader2 = new CSVReader(new FileReader(filename));
		CsvToBean csvToBean = new CsvToBean();
		csvToBean.setCsvReader(reader2);
		csvToBean.setMappingStrategy(mailboxStrategy);
		List<ClientCSV> clientCsvList = csvToBean.parse();
		String type = null;
		for (ClientCSV clientHost : clientCsvList) {
			if (type == null) {
				type = clientHost.getType();
			} else {
				if (!clientHost.getType().equalsIgnoreCase(type)) {
					System.err.println("All hosts must be the same type");
					System.exit(-1);
				}
			}
		}
		return type;
	}

	private static List parseClientFile(String filename, Types type) {
		Class targetClass = null;
		com.cleo.services.harmony.CSVReader reader;
		if (type.equals(Types.AS2))
			targetClass = AS2CSV.class;
		else if (type.equals(Types.SFTP))
			targetClass = SFTPCSV.class;
		else if (type.equals(Types.FTP))
			targetClass = FTPCSV.class;
		try {
			reader = new com.cleo.services.harmony.CSVReader(filename, targetClass);
			return reader.readFile();
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}

	protected static List<JsonElement> createAS2Hosts(String filename) {
		Gson gson = new Gson();
		ArrayList<JsonElement> hosts = new ArrayList<>();
		List<AS2CSV> as2CSVList = parseClientFile(filename, Types.AS2);
		for (AS2CSV csv : as2CSVList) {
			AS2 as2Host = new AS2();
			as2Host.alias = csv.getAlias();
			as2Host.connect.url = csv.getUrl();
			as2Host.localName = csv.getAS2From();
			as2Host.partnerName = csv.getAS2To();
			as2Host.outgoing.subject = csv.getSubject();
			as2Host.outgoing.encrypt = Boolean.valueOf(csv.getEncrypted());
			as2Host.outgoing.sign = Boolean.valueOf(csv.getSigned());
			as2Host.outgoing.receipt.type = csv.getReceipt_type();
			as2Host.outgoing.receipt.sign = Boolean.valueOf(csv.getReceipt_sign());
			as2Host.outgoing.storage.outbox = csv.getOutbox();
			as2Host.outgoing.storage.sentbox = csv.getSentbox();
			as2Host.incoming.storage.inbox = csv.getInbox();
			as2Host.incoming.storage.receivedbox = csv.getReceivedbox();
			ArrayList<ActionPOJO> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
				&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				ActionPOJO sendAction = new ActionPOJO();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend().split(actionSeparatorRegex);
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				ActionPOJO recAction = new ActionPOJO();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive().split(actionSeparatorRegex);
				actions.add(recAction);
			}
			for (Action action : csv.getActions()) {
				ActionPOJO actionPOJO = new ActionPOJO();
				actionPOJO.alias = action.getAlias();
				actionPOJO.commands = action.getCommands().split(actionSeparatorRegex);
				actionPOJO.schedule = action.getSchedule();
				actions.add(actionPOJO);
			}
			as2Host.actions = actions.toArray(new ActionPOJO[]{});
			hosts.add(gson.toJsonTree(as2Host));
		}
		return hosts;
	}

	protected static List<JsonElement> createSFTPHosts(String filename) {
		Gson gson = new Gson();
		ArrayList<JsonElement> hosts = new ArrayList<>();
		List<SFTPCSV> as2CSVList = parseClientFile(filename, Types.SFTP);
		for (SFTPCSV csv : as2CSVList) {
			SFTP sftpHost = new SFTP();
			sftpHost.alias = csv.getAlias();
			sftpHost.connect.host = csv.getHost();
			sftpHost.connect.port = csv.getPort();
			sftpHost.connect.username = csv.getUsername();
			sftpHost.connect.password = csv.getPassword();
			sftpHost.outgoing.storage.outbox = csv.getOutbox();
			sftpHost.outgoing.storage.sentbox = csv.getSentbox();
			sftpHost.incoming.storage.inbox = csv.getInbox();
			sftpHost.incoming.storage.receivedbox = csv.getReceivedbox();
			ArrayList<ActionPOJO> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
							&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				ActionPOJO sendAction = new ActionPOJO();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend().split(actionSeparatorRegex);;
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				ActionPOJO recAction = new ActionPOJO();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive().split(actionSeparatorRegex);;
				actions.add(recAction);
			}
			sftpHost.actions = actions.toArray(new ActionPOJO[]{});
			hosts.add(gson.toJsonTree(sftpHost));
		}
		return hosts;
	}

	protected static List<JsonElement> createFTPHosts(String filename) {
		Gson gson = new Gson();
		ArrayList<JsonElement> hosts = new ArrayList<>();
		List<FTPCSV> ftpCSVList = parseClientFile(filename, Types.FTP);
		for (FTPCSV csv : ftpCSVList) {
			FTP ftpHost = new FTP();
			ftpHost.alias = csv.getAlias();
			ftpHost.connect.host = csv.getHost();
			ftpHost.connect.port = csv.getPort();
			ftpHost.connect.username = csv.getUsername();
			ftpHost.connect.password = csv.getPassword();
			ftpHost.connect.defaultContentType = csv.getDataType();
			ftpHost.connect.dataChannel.mode = csv.getChannelMode();
			ftpHost.connect.dataChannel.lowPort = csv.getActiveLowPort();
			ftpHost.connect.dataChannel.highPort = csv.getActiveHighPort();
			ftpHost.outgoing.storage.outbox = csv.getOutbox();
			ftpHost.outgoing.storage.sentbox = csv.getSentbox();
			ftpHost.incoming.storage.inbox = csv.getInbox();
			ftpHost.incoming.storage.receivedbox = csv.getReceivedbox();
			ArrayList<ActionPOJO> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
							&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				ActionPOJO sendAction = new ActionPOJO();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend().split(actionSeparatorRegex);;
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				ActionPOJO recAction = new ActionPOJO();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive().split(actionSeparatorRegex);;
				actions.add(recAction);
			}
			ftpHost.actions = actions.toArray(new ActionPOJO[]{});
			hosts.add(gson.toJsonTree(ftpHost));
		}
		return hosts;
	}

	private static JsonObject  contructMailboxJSON(MailboxCSV mailboxCSV) throws JsonSyntaxException, IOException {
		
        LinkedTreeMap authFromFile = gson.fromJson(Resources.toString(Resources.getResource("mailboxTemplate.json"), Charsets.UTF_8), LinkedTreeMap.class);
        authFromFile.put("host", mailboxCSV.getHost());
        authFromFile.put("username", mailboxCSV.getUserID());
        ((LinkedTreeMap)authFromFile.get("accept")).put("password", mailboxCSV.getPassword());

        //authFromFile.put("accept/password", line[13]);
        if(mailboxCSV.getDefaultHomeDir().equals("Yes")) {
        	((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("dir")).put("default", mailboxCSV.getCustomHomeDir());
        }
        else {
        	((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("dir")).put("override", mailboxCSV.getCustomHomeDir());
        }
        if(!mailboxCSV.getWhitelistIP().isEmpty()) {
        	ArrayList wl = new ArrayList();
			for(String ipaddr : mailboxCSV.getWhitelistIP().split(";")) {
				LinkedTreeMap tr = new LinkedTreeMap();
				tr.put("ipAddress", ipaddr);
				wl.add(tr);
			}
			((LinkedTreeMap)authFromFile.get("accept")).put("whitelist",wl);
		}
        authFromFile.put("notes", mailboxCSV.getHostNotes());
		if(!mailboxCSV.getOtherFolder().isEmpty()) {
			for(String path : mailboxCSV.getOtherFolder().split(";")) {
				LinkedTreeMap tr = new LinkedTreeMap();
				tr.put("usage", "other");
				tr.put("path", path);
				((ArrayList)((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("subfolders")).get("default")).add(tr);
			}
		}
        authFromFile.put("email", mailboxCSV.getEmail());

		authFromFile.put("actions", createActions(mailboxCSV));
        
        return gson.toJsonTree(authFromFile).getAsJsonObject();
	}

	//private static LinkedTreeMap createActions(String collectAlias, String[] collectCommands, String receiveAlias, String[] receiveCommands) {
	private static LinkedTreeMap createActions(MailboxCSV mailboxCSV) {
		String actionSeparatorRegex = "[\\|;]";
		String collectAlias = mailboxCSV.getCreateCollectName();
		String[] collectCommands = mailboxCSV.getActionCollect().split(actionSeparatorRegex);
		String receiveAlias = mailboxCSV.getCreateReceiveName();
		String[] receiveCommands = mailboxCSV.getActionReceive().split(actionSeparatorRegex);
		LinkedTreeMap actions = new LinkedTreeMap();
		if (!collectAlias.equalsIgnoreCase("NA")) {
			LinkedTreeMap collect = new LinkedTreeMap();
			collect.put("alias", collectAlias);
			collect.put("commands", collectCommands);
			if (!mailboxCSV.getSchedule_Collect().isEmpty() && !mailboxCSV.getSchedule_Collect().equalsIgnoreCase("none")
							&& !mailboxCSV.getSchedule_Collect().equalsIgnoreCase("no")) {
				if (mailboxCSV.getSchedule_Collect().equalsIgnoreCase("polling"))
					collect.put("schedule", "on file continuously");
				else
					collect.put("schedule", mailboxCSV.getSchedule_Collect());
			}
			actions.put(collectAlias, collect);
		}

		if (!receiveAlias.equalsIgnoreCase("NA")) {
			LinkedTreeMap receive = new LinkedTreeMap();
			receive.put("alias", receiveAlias);
			receive.put("commands", receiveCommands);
			if (!mailboxCSV.getSchedule_Receive().isEmpty() && !mailboxCSV.getSchedule_Receive().equalsIgnoreCase("none")
							&& !mailboxCSV.getSchedule_Receive().equalsIgnoreCase("no")) {
				if (mailboxCSV.getSchedule_Receive().equalsIgnoreCase("polling"))
					receive.put("schedule", "on file continuously");
				else
					receive.put("schedule", mailboxCSV.getSchedule_Receive());
			}
			actions.put(receiveAlias, receive);
		}

		return actions;
	}

	private static JsonObject contructUsrGrpJSON(String[] line) throws JsonSyntaxException, IOException {
		LinkedTreeMap authFromFile = gson.fromJson(Resources.toString(Resources.getResource("groupTemplate.json"), Charsets.UTF_8), LinkedTreeMap.class);
		authFromFile.put("alias", line[0]);
		if(!line[1].isEmpty())
			authFromFile.put("resourceFolder", line[0]);
		if(!line[2].isEmpty())
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("dir")).put("default", line[2]);
		if(!line[3].isEmpty()) {
			LinkedTreeMap tr = new LinkedTreeMap();
			tr.put("usage", "download");
			tr.put("path", line[3]);
			((ArrayList)((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("subfolders")).get("default")).add(tr);
			//((ArrayList)((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("subfolders")).get("default")).add(new LinkedTreeMap().put("path", line[3]));
		}
		if(!line[4].isEmpty()) {
			LinkedTreeMap tr = new LinkedTreeMap();
			tr.put("usage", "upload");
			tr.put("path", line[4]);
			((ArrayList)((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("subfolders")).get("default")).add(tr);
		}
		if(!line[5].isEmpty()) {
			for(String path :line[5].split(";")) {
				LinkedTreeMap tr = new LinkedTreeMap();
				tr.put("usage", "other");
				tr.put("path", path);
				((ArrayList)((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("home")).get("subfolders")).get("default")).add(tr);
			}
		}
		if(!line[6].isEmpty()) {
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("outgoing")).get("storage")).put("sentbox", line[6]);
		}
		if(!line[7].isEmpty()) {
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("incoming")).get("storage")).put("receivedbox", line[7]);
		}
		if(line[8].equals("Yes")) {
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("accept")).get("ftp")).put("enabled", true);
		}
		if(line[9].equals("Yes")) {
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("accept")).get("sftp")).put("enabled", true);
		}
		if(line[10].equals("Yes")) {
			((LinkedTreeMap)((LinkedTreeMap)authFromFile.get("accept")).get("http")).put("enabled", true);
		}
		if(!line[11].isEmpty()) {
			((LinkedTreeMap)authFromFile.get("home")).put("access", line[11].toLowerCase());
		}
		return gson.toJsonTree(authFromFile).getAsJsonObject();
	}
}
