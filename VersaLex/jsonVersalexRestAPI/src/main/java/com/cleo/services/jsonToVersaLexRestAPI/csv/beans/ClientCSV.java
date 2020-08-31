package com.cleo.services.harmony;

import com.cleo.services.harmony.Action;
import lombok.Getter;
import lombok.Setter;

public class ClientCSV {

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
  private Action[] actions;
}
