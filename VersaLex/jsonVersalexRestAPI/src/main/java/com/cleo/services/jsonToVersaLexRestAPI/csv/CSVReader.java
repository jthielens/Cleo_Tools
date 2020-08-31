package com.cleo.services.harmony;

import com.cleo.services.harmony.Action;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class CSVReader {
  private String filename;
  private Class targetClass;
  private HashMap<String, Integer> targetClassMethods;

  private final static int STRING = 0;
  private final static int INT = 1;


  public void loadTargetClassMethods() throws IntrospectionException {
    targetClassMethods = new HashMap<>();
    Method[] methods = targetClass.getMethods();
    for (Method method : methods) {
      String methodName = method.getName();
      if (methodName.startsWith("set")) {
        Class parameterClass = method.getParameterTypes()[0];
        if (parameterClass.getName().equals("int")) {
          targetClassMethods.put(methodName, INT);
        }else if (parameterClass.getName().equals("java.lang.String")) {
          targetClassMethods.put(methodName, STRING);
        }
      }
    }
  }

  public Action[] actionHashToArray(HashMap<String, Action> actionsMap) {
    List<Action> actionList = new ArrayList();
    Set<String> keys = actionsMap.keySet();
    for (String key : keys) {
      actionList.add(actionsMap.get(key));
    }
    return actionList.toArray(new Action[]{});
  }

  public String fixName(String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  public List readFile() throws Exception {
    List targetObjectList = new ArrayList();
    List<String> lines = Files.readAllLines(Paths.get(this.filename));
    if (lines.size() <= 0)
      throw new Exception("Empty CSV file!");

    HashMap<String, Action> actions = new HashMap<>();

    String[] columns = lines.get(0).split(",");
    for (int i = 1; i < lines.size(); i++) {
      Object target = targetClass.newInstance();
      String[] values = lines.get(i).split(",");
      for (int j = 0; j < values.length; j++) {
        String column = columns[j];
        if (column.contains("action_")) {
          String methodName = "set" + fixName(column.split("_")[2]);
          String number = column.split("_")[1];
          if (!actions.containsKey(number)) {
            actions.put(number, new Action());
          }
          Method method = Action.class.getMethod(methodName, java.lang.String.class);
          method.invoke(actions.get(number), values[j]);
        } else {
          String methodName = "set" + fixName(column);
          if (this.targetClassMethods.containsKey(methodName)) {
            Class parameter = null;
            switch (this.targetClassMethods.get(methodName)) {
              case STRING:
                target.getClass().getMethod(methodName, java.lang.String.class).invoke(target, values[j]);
                break;
              case INT:
                target.getClass().getMethod(methodName, int.class).invoke(target, Integer.parseInt(values[j]));
                break;
              default:
            }
          }
        }
      }
      Method actionMethod = target.getClass().getMethod("setActions", new Class[]{Action[].class});
      actionMethod.invoke(target, new Object[]{this.actionHashToArray(actions)});
      targetObjectList.add(target);
    }
    return targetObjectList;
  }

  public CSVReader(String filename, Class targetClass) throws Exception  {
    this.filename = filename;
    this.targetClass = targetClass;
    loadTargetClassMethods();
  }
}
