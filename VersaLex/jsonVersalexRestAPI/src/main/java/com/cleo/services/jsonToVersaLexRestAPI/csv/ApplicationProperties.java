package com.cleo.services.harmony;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ApplicationProperties {
	
	static Properties appProps = new Properties();
	
	public void SearchProperties() throws FileNotFoundException, IOException {
		readProperties();
	
	}
	
	public void readProperties() throws FileNotFoundException, IOException {
	//String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
	String appConfigPath = "app.properties";
	appProps.load(new FileInputStream(appConfigPath));
	}

}
