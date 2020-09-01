package com.cleo.services.jsonToVersaLexRestAPI.pojo;

public class FTP {

  public String type = "ftp";
  public String alias;
  public FTP.Incoming incoming;
  public FTP.Outgoing outgoing;
  public FTP.Connect connect;
  public ActionPOJO[] actions;

  public FTP(){
    this.incoming = new FTP.Incoming();
    this.outgoing = new FTP.Outgoing();
    this.connect = new FTP.Connect();
  }

  public class Connect {
    public String host;
    public int port;
    public String username;
    public String password;
    public String defaultContentType;
    public DataChannel dataChannel;

    public class DataChannel {
      public String mode;
      public int lowPort;
      public int highPort;

      public DataChannel() { }
    }
    public Connect() {
      this.dataChannel = new DataChannel();
    }
  }

  public class Outgoing {
    public Storage storage;

    public class Storage {
      public String outbox;
      public String sentbox;

      public Storage() {}
    }

    public Outgoing() {
      this.storage = new Storage();
    }

  }

  public class Incoming {
    public Storage storage;

    public class Storage {
      public String inbox;
      public String receivedbox;

      public Storage() {}
    }

    public Incoming() {
      this.storage = new Storage();
    }

  }
}
