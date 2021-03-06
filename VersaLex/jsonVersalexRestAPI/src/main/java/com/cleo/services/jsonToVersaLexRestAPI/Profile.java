package com.cleo.services.jsonToVersaLexRestAPI;

public class Profile {
    private String host = null;
    private int port = -1;
    private boolean secure = false;
    private boolean insecure = false;
    private String username = null;
    private String password = null;
    private String exportPassword = null;

    public String getHost() {
        return host;
    }
    public Profile setHost(String host) {
        this.host = host;
        return this;
    }
    public int getPort() {
        return port;
    }
    public Profile setPort(int port) {
        this.port = port;
        return this;
    }
    public boolean isSecure() {
        return secure;
    }
    public Profile setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }
    public boolean isInsecure() {
        return insecure;
    }
    public Profile setInsecure(boolean insecure) {
        this.insecure = insecure;
        return this;
    }
    public String getUsername() {
        return username;
    }
    public Profile setUsername(String username) {
        this.username = username;
        return this;
    }
    public String getPassword() {
        return password;
    }
    public Profile setPassword(String password) {
        this.password = password;
        return this;
    }
    public String getExportPassword() {
        return exportPassword;
    }
    public Profile setExportPassword(String exportPassword) {
        this.exportPassword = exportPassword;
        return this;
    }
}
