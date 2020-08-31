package com.cleo.services.harmony.pojo;

public class AS2 {

  public String type = "as2";
  public String alias;
  public String localName;
  public String partnerName;
  public AS2.Accept accept;
  public AS2.Incoming incoming;
  public AS2.Outgoing outgoing;
  public AS2.Connect connect;
  public ActionPOJO[] actions;

  public AS2(){
    this.accept = new AS2.Accept();
    this.incoming = new AS2.Incoming();
    this.outgoing = new AS2.Outgoing();
    this.connect = new AS2.Connect();
  }

  public class Connect {
    public String url;
    public Connect(){}
  }

  public class Outgoing {
    public String subject;
    public boolean encrypt;
    public boolean sign;
    public Receipt receipt;
    public Storage storage;

    public class Receipt {
      public String type;
      public boolean sign;

      public Receipt() {}
    }

    public class Storage {
      public String outbox;
      public String sentbox;

      public Storage() {}
    }

    public Outgoing() {
      this.receipt = new Receipt();
      this.storage = new Storage();
    }

  }

  public class Incoming {
    public boolean requireEncryption;
    public boolean requireSignature;
    public boolean requireReceiptSignature;
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

  public class Accept {
    public boolean requireSecurePort;
    public Accept(){
    }
  }
}
