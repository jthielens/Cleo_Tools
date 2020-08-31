package com.cleo.services.harmony.pojo;

public class SFTP {

  public String type = "sftp";
  public String alias;
  public SFTP.Incoming incoming;
  public SFTP.Outgoing outgoing;
  public SFTP.Connect connect;
  public ActionPOJO[] actions;

  public SFTP(){
    this.incoming = new SFTP.Incoming();
    this.outgoing = new SFTP.Outgoing();
    this.connect = new SFTP.Connect();
  }

  public class Connect {
    public String host;
    public int port;
    public String username;
    public String password;
    public Connect(){}
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
