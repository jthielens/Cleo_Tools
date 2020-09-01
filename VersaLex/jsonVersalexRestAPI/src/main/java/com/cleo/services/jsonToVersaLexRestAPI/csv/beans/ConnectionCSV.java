package com.cleo.services.jsonToVersaLexRestAPI.csv.beans;

import lombok.Getter;
import lombok.Setter;

public class ConnectionCSV {

  @Getter@Setter
  private String type;
  @Getter@Setter
  private String alias;
  @Getter@Setter
  private String inbox;
  @Getter@Setter
  private String outbox;
  @Getter@Setter
  private String sentbox;
  @Getter@Setter
  private String receivedbox;
  @Getter@Setter
  private ActionCSV[] actions;
}
