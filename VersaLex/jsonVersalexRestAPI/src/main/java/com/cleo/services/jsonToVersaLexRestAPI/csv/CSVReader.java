package com.cleo.services.jsonToVersaLexRestAPI.csv;

import java.beans.IntrospectionException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.cleo.services.jsonToVersaLexRestAPI.csv.beans.ActionCSV;

public class CSVReader <T> {
    private String[] lines;
    private Class<T> targetClass;
    private HashMap<String, ParameterType> targetClassMethods;

    private enum ParameterType {
        STRING, INT
    };

    public void loadTargetClassMethods() throws IntrospectionException {
        targetClassMethods = new HashMap<>();
        Method[] methods = targetClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("set")) {
                Class<?> parameterClass = method.getParameterTypes()[0];
                if (parameterClass.getName().equals("int")) {
                    targetClassMethods.put(methodName, ParameterType.INT);
                } else if (parameterClass.getName().equals("java.lang.String")) {
                    targetClassMethods.put(methodName, ParameterType.STRING);
                }
            }
        }
    }

    public ActionCSV[] actionHashToArray(HashMap<String, ActionCSV> actionsMap) {
        return actionsMap.values().toArray(new ActionCSV[actionsMap.size()]);
    }

    public String fixName(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public List<T> readFile() throws Exception {
        List<T> targetObjectList = new ArrayList<>();
        if (lines.length <= 0)
            throw new Exception("Empty CSV file!");

        HashMap<String, ActionCSV> actions = new HashMap<>();

        String[] columns = lines[0].split(",");
        for (int i = 1; i < lines.length; i++) {
            T target = targetClass.newInstance();
            String[] values = lines[i].split(",");
            for (int j = 0; j < values.length; j++) {
                String column = columns[j];
                if (column.contains("action_")) {
                    String[] actionparts = column.split("_", 3);
                    String methodName = "set" + actionparts[2];
                    String number = actionparts[1];
                    if (!actions.containsKey(number)) {
                        actions.put(number, new ActionCSV());
                    }
                    Method method = ActionCSV.class.getMethod(methodName, java.lang.String.class);
                    method.invoke(actions.get(number), values[j]);
                } else {
                    String methodName = "set" + fixName(column);
                    if (this.targetClassMethods.containsKey(methodName)) {
                        switch (this.targetClassMethods.get(methodName)) {
                        case STRING:
                            target.getClass().getMethod(methodName, java.lang.String.class).invoke(target, values[j]);
                            break;
                        case INT:
                            target.getClass().getMethod(methodName, int.class).invoke(target,
                                    Integer.parseInt(values[j]));
                            break;
                        default:
                        }
                    }
                }
            }
            Method actionMethod = target.getClass().getMethod("setActions", new Class[] { ActionCSV[].class });
            actionMethod.invoke(target, new Object[] { this.actionHashToArray(actions) });
            targetObjectList.add(target);
        }
        return targetObjectList;
    }

    public CSVReader(String content, Class<T> targetClass) throws Exception {
        this.lines = content.split("\r?\n");
        this.targetClass = targetClass;
        loadTargetClassMethods();
    }

    public CSVReader(Path filename, Class<T> targetClass) throws Exception {
        this(new String(Files.readAllBytes(filename)), targetClass);
    }
}
