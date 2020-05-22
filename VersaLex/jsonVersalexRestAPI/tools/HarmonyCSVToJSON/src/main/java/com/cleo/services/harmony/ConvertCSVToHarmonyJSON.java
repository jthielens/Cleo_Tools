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
import com.cleo.services.harmony.pojo.Action;
import com.cleo.services.harmony.pojo.SFTP;
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

	public static Gson gson = new Gson();

	public static enum  Types {
		AS2,
		SFTP,
		FTP
	}
	
	public static void main(String[] args) throws IOException, CsvException {
		AS2 as2 = new AS2();
		as2.alias = "test";
		as2.accept.requireSecurePort = true;
		System.out.println(new Gson().toJson(as2));
		System.exit(0);
		new ApplicationProperties().readProperties();
		String mailboxFilename = ApplicationProperties.appProps.getProperty("mailBoxFile");
		System.out.println("Finished reading the app config file...");
		
        JsonArray jsonArr = new JsonArray();
        
		CSVReader grpReader = new CSVReader(new FileReader(ApplicationProperties.appProps.getProperty("groupFile")));
		List<String[]> grpElements = grpReader.readAll();
		for(String[] line: grpElements) {
        	if(!line[0].equals("UserAlias"))
        		jsonArr.add(contructUsrGrpJSON(line));
        }

		List<String> lines = Files.readAllLines(Paths.get(mailboxFilename));
		if (lines.size() > 0 && (lines.get(0).contains("type") || lines.get(0).contains("Type"))) {
			/*List<ClientCSV> clientHosts = parseClientFile(ApplicationProperties.appProps.getProperty("mailBoxFile"));
			if (clientHosts != null) {
				for (ClientCSV clientHost : clientHosts) {
					if ()
				}
			}*/
			String type = getFileType(mailboxFilename);
			if (type.equalsIgnoreCase(Types.AS2.name())) {
				List<JsonElement> as2Jsons = createAS2Hosts(mailboxFilename);
				for (JsonElement as2Host : as2Jsons) {
					jsonArr.add(as2Host);
				}
			} else if (type.equalsIgnoreCase(Types.SFTP.name())) {
				List<JsonObject> ftpJsons = createSFTPHosts(mailboxFilename);
				for (JsonObject ftpHost : ftpJsons) {
					jsonArr.add(ftpHost);
				}
			} else if (type.equalsIgnoreCase(Types.FTP.name())) {
				List<JsonObject> sftpJsons = createFTPHosts(mailboxFilename);
				for (JsonObject sftpHost : sftpJsons) {
					jsonArr.add(sftpHost);
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
		HeaderColumnNameMappingStrategy mailboxStrategy = new HeaderColumnNameMappingStrategy<>();
		if (type.equals(Types.AS2))
			mailboxStrategy.setType(AS2CSV.class);
		else if (type.equals(Types.SFTP))
			mailboxStrategy.setType(SFTPCSV.class);
		else if (type.equals(Types.FTP))
			mailboxStrategy.setType(FTPCSV.class);
		CSVReader reader2 = null;
		try {
			reader2 = new CSVReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			return new ArrayList<>();
		}
		CsvToBean csvToBean = new CsvToBean();
		csvToBean.setCsvReader(reader2);
		csvToBean.setMappingStrategy(mailboxStrategy);
		return csvToBean.parse();
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
			ArrayList<Action> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
				&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				Action sendAction = new Action();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend();
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				Action recAction = new Action();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive();
				actions.add(recAction);
			}
			as2Host.actions = actions.toArray(new Action[]{});
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
			ArrayList<Action> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
							&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				Action sendAction = new Action();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend();
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				Action recAction = new Action();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive();
				actions.add(recAction);
			}
			sftpHost.actions = actions.toArray(new Action[]{});
			hosts.add(gson.toJsonTree(sftpHost));
		}
		return hosts;
	}

	protected static List<JsonElement> createFTPHosts(String filename) {
		Gson gson = new Gson();
		ArrayList<JsonElement> hosts = new ArrayList<>();
		List<FTPCSV> ftpCSVList = parseClientFile(filename, Types.FTP);
		for (FTPCSV csv : ftpCSVList) {
			SFTP ftpHost = new SFTP();
			ftpHost.alias = csv.getAlias();
			ftpHost.connect.host = csv.getHost();
			ftpHost.connect.port = csv.getPort();
			ftpHost.connect.username = csv.getUsername();
			ftpHost.connect.password = csv.getPassword();
			ftpHost.outgoing.storage.outbox = csv.getOutbox();
			ftpHost.outgoing.storage.sentbox = csv.getSentbox();
			ftpHost.incoming.storage.inbox = csv.getInbox();
			ftpHost.incoming.storage.receivedbox = csv.getReceivedbox();
			ArrayList<Action> actions = new ArrayList<>();
			if (csv.getCreateSendName() != null && !csv.getCreateSendName().isEmpty()
							&& csv.getActionSend() != null && ! csv.getActionSend().isEmpty()){
				Action sendAction = new Action();
				sendAction.alias = csv.getCreateSendName();
				sendAction.commands = csv.getActionSend();
				actions.add(sendAction);
			}
			if (csv.getCreateReceiveName() != null && !csv.getCreateReceiveName().isEmpty()
							&& csv.getActionReceive() != null && ! csv.getActionReceive().isEmpty()){
				Action recAction = new Action();
				recAction.alias = csv.getCreateReceiveName();
				recAction.commands = csv.getActionReceive();
				actions.add(recAction);
			}
			ftpHost.actions = actions.toArray(new Action[]{});
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
