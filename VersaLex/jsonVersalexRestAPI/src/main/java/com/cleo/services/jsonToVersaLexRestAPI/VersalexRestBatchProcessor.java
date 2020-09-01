package com.cleo.services.jsonToVersaLexRestAPI;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cleo.services.jsonToVersaLexRestAPI.csv.ConvertCSVToHarmonyJSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;

public class VersalexRestBatchProcessor {
    public enum Option {generatePass, update};
    public VersalexRestBatchProcessor set(Option o) {
        return set(o, true);
    }
    public VersalexRestBatchProcessor set(Option o, boolean value) {
        if (value) {
            options.add(o);
        } else {
            options.remove(o);
        }
        return this;
    }
    public VersalexRestBatchProcessor setExportPassword(String exportPassword) {
        this.exportPassword = exportPassword;
        return this;
    }

    public enum Operation {
        add ("created"),
        list ("found"),
        update ("updating"),
        delete ("deleted");

        private String tag;
        private Operation(String tag) {
            this.tag = tag;
        }
        public String tag() {
            return tag;
        }
    };

    private REST api;
    private EnumSet<Option> options;
    private String exportPassword;

    public enum AuthenticatorType {nativeUser, systemLdap, authConnector};
    private static final Set<String> AUTH_TYPES = EnumSet.allOf(AuthenticatorType.class)
            .stream()
            .map(AuthenticatorType::name)
            .collect(Collectors.toSet());

    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private Map<String, ObjectNode> authenticatorCache = new HashMap<>();

    private void createActions(ObjectNode actions, ObjectNode resource) throws Exception {
        if (actions != null && actions.size() > 0) {
            ObjectNode updated = (ObjectNode)resource.get("actions");
            if (updated == null) {
                updated = resource.objectNode();
            } else {
                updated = updated.deepCopy();
            }
            String type = REST.getSubElementAsText(resource, "meta.resourceType");
            Iterator<JsonNode> elements = actions.elements();
            while (elements.hasNext()) {
                ObjectNode action = (ObjectNode) elements.next();
                if (action != null && action.isObject()) {
                    String actionName = REST.getSubElementAsText(action, "alias", "");
                    if (!actionName.isEmpty() && !actionName.equals("NA")) {
                        action.put("enabled", true);
                        action.put("type", "Commands");
                        switch (type) {
                        case "user":
                            action.putObject("authenticator")
                                    .put("href", REST.getSubElementAsText(resource, "_links.authenticator.href"))
                                    .putObject("user").put("href", REST.getHref(resource));
                            break;
                        case "authenticator":
                            action.putObject("authenticator").put("href", REST.getHref(resource));
                            break;
                        default:
                        }
                        action.putObject("connection").put("href", REST.getHref(resource));
                        String schedule = REST.getSubElementAsText(action, "schedule", "");
                        if (schedule.isEmpty() || schedule.equals("none") || schedule.equals("no")) {
                            action.remove("schedule");
                        }
                        Operation operation = Operation.valueOf(REST.asText(action.remove("operation"), "add"));
                        JsonNode existing = updated.get(actionName);
                        if (existing == null) {
                            if (!operation.equals(Operation.delete)) {
                                ObjectNode newAction = api.createAction(action);
                                updated.set(actionName, newAction);
                            }
                        } else {
                            if (operation.equals(Operation.delete)) {
                                api.delete(existing);
                                updated.remove(actionName);
                            } else {
                                ObjectNode newAction = api.put(actions, existing);
                                updated.replace(actionName, newAction);
                            }
                        }
                    }
                }
            }
            if (updated.size() > 0) {
                resource.set("actions", updated);
            } else {
                resource.remove("actions");
            }
        }
    }

    private static String generatePassword() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~!@#$%^&*()-_=+[{]}\\|;:\'\"<.>/?";
        SecureRandom random = new SecureRandom();
        return random.ints(0, characters.length()).limit(20)
                .mapToObj(i -> String.valueOf(characters.charAt(i))).collect(Collectors.joining());
    }

    private static ObjectNode generatePasswordForUser(ObjectNode user) {
        ObjectNode accept = (ObjectNode)user.get("accept");
        if (accept == null) {
            accept = user.putObject("accept");
        }
        accept.put("password", generatePassword());
        return user;
    }

    private ObjectNode generatedPassword(String authenticator, ObjectNode entry) {
        //   alias: authenticator
        //   username: username
        //   email: email
        //   password: encrypted password
        String password = REST.getSubElement(entry, "accept.password").asText();
        String encrypted = Strings.isNullOrEmpty(exportPassword)
                ? password
                : OpenSSLCrypt.encrypt(exportPassword, password);
        ObjectNode result = mapper.createObjectNode();
        result.put("alias", authenticator);
        result.put("username", entry.get("username").asText());
        result.put("email", entry.get("email").asText());
        result.put("password", encrypted);
        return result;
    }

    private ObjectNode updateResource(ObjectNode object, ObjectNode updates) throws Exception {
        String type = REST.getSubElementAsText(object, "meta.resourceType", "");
        ObjectNode updated = object.deepCopy();
        if (type.equals("user")) {
            updated.remove("alias"); // this is used as the authenticator alias, which isn't present in the real API
            updates.remove("alias");
        }
        JsonNode actions = updated.remove("actions");
        updated.setAll(updates);
        String href = REST.getHref(updated);
        cleanup(updated);
        ObjectNode result = api.put(updated, href);
        if (actions != null) {
            result.set("actions", actions);
        }
        return result;
    }

    private static ObjectNode loadTemplate(String template) throws Exception {
        return (ObjectNode) mapper.readTree(Resources.toString(Resources.getResource(template), Charsets.UTF_8));
    }

    private ObjectNode getAuthenticator(String alias) throws Exception {
        ObjectNode authenticator;
        if (authenticatorCache.containsKey(alias)) {
            authenticator = authenticatorCache.get(alias);
        } else {
            authenticator = api.getAuthenticator(alias);
            if (authenticator != null) {
                authenticatorCache.put(alias, authenticator);
            }
        }
        return authenticator;
    }

    private String getAliasOrUsername(ObjectNode object) {
        String alias = REST.getSubElementAsText(object, "username");
        if (alias == null) {
            alias = REST.getSubElementAsText(object, "alias");
        }
        return alias;
    }

    private ObjectNode createAuthenticatorFromTemplate(String alias) throws Exception {
        ObjectNode authTemplate = loadTemplate("template_authenticator.yaml");
        authTemplate.put("alias", alias);
        ObjectNode authenticator = api.createAuthenticator(authTemplate);
        if (authenticator == null) {
            throw new ProcessingException("authenticator not created");
        }
        authenticatorCache.put(alias, authenticator);
        return authenticator;
    }

    private ObjectNode normalizeActions(JsonNode actions) throws Exception {
        if (actions != null && actions.isArray()) {
            // convert from [ {alias=x, ...}, {alias=y, ...} ] to { x:{alias=x, ...},
            // y:{alias=y, ...} }
            ObjectNode map = ((ArrayNode) actions).objectNode();
            Iterator<JsonNode> elements = ((ArrayNode) actions).elements();
            while (elements.hasNext()) {
                JsonNode action = elements.next();
                String alias = REST.getSubElementAsText(action, "alias");
                if (alias == null) {
                    throw new ProcessingException("action found with missing alias: " + action.toPrettyString());
                }
                map.set(alias, action);
            }
            actions = map;
        }
        return (ObjectNode) actions;
    }

	/*------------------------------------------------------------------------*
	 * Get/List Operations                                                    *
	 *------------------------------------------------------------------------*/

    private ObjectNode injectActions(ObjectNode resource) throws Exception {
        JsonNode actionlinks = resource.path("_links").path("actions");
        if (!actionlinks.isMissingNode()) {
            ObjectNode actions = resource.objectNode();
            Iterator<JsonNode> elements = actionlinks.elements();
            while (elements.hasNext()) {
                JsonNode actionlink = elements.next();
                ObjectNode action = api.get(REST.getSubElementAsText(actionlink, "href"));
                actions.set(REST.getSubElementAsText(action, "alias"), action);
            }
            if (actions.size() > 0) {
                resource.set("actions", actions);
            }
        }
        return resource;
    }

    private ObjectNode listUser(String username) throws Exception {
        ObjectNode user = api.getUser(username);
        if (user == null) {
            throw new ProcessingException("user "+username+" not found");
        }
        return listUser(user);
    }

    private ObjectNode listUser(ObjectNode user) throws Exception {
        injectActions(user);
        // inject Authenticator
        JsonNode authenticatorlink = user.path("_links").path("authenticator");
        if (!authenticatorlink.isMissingNode()) {
            ObjectNode authenticator = api.get(REST.getSubElementAsText(authenticatorlink, "href"));
            String alias = REST.getSubElementAsText(authenticator, "alias");
            if (alias != null) {
                // set host, but reorder things to get it near the top
                ObjectNode update = user.objectNode();
                update.set("id", user.get("id"));
                update.set("username", user.get("username"));
                update.set("email",  user.get("email"));
                update.put("alias", alias);
                update.setAll(user);
                user = update;
            }
        }
        return user;
    }

    private List<ObjectNode> listAuthenticator(String alias, boolean includeUsers) throws Exception {
        ObjectNode authenticator = api.getAuthenticator(alias);
        if (authenticator == null) {
            throw new ProcessingException("authenticator "+alias+" not found");
        }
        injectActions(authenticator);
        List<ObjectNode> result = new ArrayList<>();
        // collect users, if requested
        if (includeUsers) {
            String userlink = REST.getSubElementAsText(authenticator, "_links.users.href");
			REST.JsonCollection users = api.new JsonCollection(userlink);
			while (users.hasNext()) {
			    ObjectNode user = users.next();
			    user = listUser(user);
			    result.add(user);
			}
        }
        // suppress read-only fields and some defaults
        result.add(0, authenticator);
        return result;
    }

    private ObjectNode listConnection(String alias) throws Exception {
        ObjectNode connection = api.getConnection(alias);
        if (connection == null) {
            throw new ProcessingException("connection "+alias+" not found");
        }
        return listConnection(connection);
    }

    private ObjectNode listConnection(ObjectNode connection) throws Exception {
        return injectActions(connection);
    }

	/*------------------------------------------------------------------------*
	 * Cleanups -- Remove Metadata and Defaults                               *
	 *------------------------------------------------------------------------*/

    private ObjectNode cleanupActions(ObjectNode resource) {
        ObjectNode actions = (ObjectNode)resource.get("actions");
        if (actions != null) {
            Iterator<JsonNode> elements = actions.elements();
            while (elements.hasNext()) {
                ObjectNode action = (ObjectNode)elements.next();
                REST.removeElements(action,
                        "active",
                        "editable",
                        "runnable",
                        "running",
                        "ready",
                        "enabled",
                        "type=Commands",
                        "authenticator",
                        "connection",
                        "meta",
                        "_links");
            }
        }
        return resource;
    }

    private ObjectNode cleanupUser(ObjectNode user) {
        REST.removeElements(user,
                "active",
                "editable",
                "runnable",
                "ready",
                "enabled",
                "home.dir.default",
                "home.subfolders.default",
                "accept.lastPasswordReset",
                "accept.sftp.auth=[{\"type\":\"userPwd\"}]",
                "outgoing.partnerPackaging=false",
                "incoming.partnerPackaging=false",
                "meta",
                "_links");
        cleanupActions(user);
        return user;
    }

    private ObjectNode cleanupAuthenticator(ObjectNode authenticator) {
        REST.removeElements(authenticator,
                "active",
                "editable",
                "runnable",
                "ready",
                "enabled",
                "home.enabled=true",
                "home.dir.default",
                "home.subfolders.default=---\n- usage: download\n  path: inbox\\\n- usage: upload\n  path: outbox\\\n",
                "home.access=file",
                "privileges.transfers.view=true",
                "privileges.unify.enabled=false",
                "privileges.invitations.enabled=false",
                "privileges.twoFactorAuthentication.enabled=false",
                "incoming.filters.fileNamesPattern=\"*\"",
                "accept.security.requireIPFilter=false",
                "accept.security.passwordRules.enforce=false",
                "accept.security.passwordRules.minLength=8",
                "accept.security.passwordRules.cannotContainUserName=true",
                "accept.security.passwordRules.minUpperChars=1",
                "accept.security.passwordRules.minLowerChars=1",
                "accept.security.passwordRules.minNumericChars=1",
                "accept.security.passwordRules.minSpecialChars=1",
                "accept.security.passwordRules.noRepetitionCount=3",
                "accept.security.passwordRules.requirePasswordResetBeforeFirstUse=false",
                "accept.security.passwordRules.expiration.enabled=true",
                "accept.security.passwordRules.expiration.expiresDays=60",
                "accept.security.passwordRules.lockout.enabled=false",
                "accept.security.passwordRules.lockout.afterFailedAttempts=5",
                "accept.security.passwordRules.lockout.withinSeconds=60",
                "accept.security.passwordRules.lockout.lockoutMinutes=15",
                "accept.ftp.enabled=true",
                "accept.ftp.passiveModeUseExternalIP=false",
                "accept.ftp.autoDeleteDownloadedFile=false",
                "accept.ftp.activeModeSourcePort=0",
                "accept.ftp.ignoreDisconnectWithoutQuit=false",
                "accept.ftp.triggerAtUpload=false",
                "accept.sftp.enabled=true",
                "accept.sftp.prefixHome=false",
                "accept.http.enabled=true",
                "accept.requireSecurePort=false",
                "meta",
                "_links");
        cleanupActions(authenticator);
        return authenticator;
    }

    private ObjectNode cleanupConnection(ObjectNode connection) {
        // TODO: this may need more work, but maybe not
        REST.removeElements(connection,
                "active",
                "editable",
                "runnable",
                "ready",
                "enabled",
                "meta",
                "_links");
        cleanupActions(connection);
        return connection;
    }

    private ObjectNode cleanup(ObjectNode resource) {
        String type = REST.getSubElementAsText(resource, "meta.resourceType", "");
        switch (type) {
        case "user":
            return cleanupUser(resource);
        case "authenticator":
            return cleanupAuthenticator(resource);
        case "connection":
            return cleanupConnection(resource);
        default:
            return resource;
        }
    }

    private ArrayNode cleanup(ArrayNode list) {
        Iterator<JsonNode> elements = list.elements();
        while (elements.hasNext()) {
            ObjectNode element = (ObjectNode)elements.next();
            cleanup(element);
        }
        return list;
    }

	/*------------------------------------------------------------------------*
	 * Main File Processor                                                    *
	 *------------------------------------------------------------------------*/

    /**
     * Adds a "result" object containing "status" (success or error) and
     * "message" fields to the top of an ObjectNode
     * @param node the node to modify
     * @param success {@code true} for "success" else "error"
     * @param message the (optional) "message" to add
     * @return the modified node
     */
    private ObjectNode insertResult(ObjectNode node, boolean success, String message) {
        ObjectNode result = REST.setSubElement((ObjectNode)node.get("result"), "status", success ? "success" : "error");
        REST.setSubElement(result, "message", message);
        return ((ObjectNode)mapper.createObjectNode().set("result", result)).setAll(node);
    }

    private static class StackTraceCapture extends PrintStream {
        private boolean first = true;
        private ArrayNode trace;
        public StackTraceCapture(ArrayNode trace) {
            super (ByteStreams.nullOutputStream());
            this.trace = trace;
        }
        public void print(String s) {
            if (first) {
                first = false;
            } else {
                trace.add(s.replaceAll("^\\t*", ""));
            }
        }
    }

    private ObjectNode insertResult(ObjectNode node, boolean success, Exception e) {
        ObjectNode update = insertResult(node, success, e.getMessage());
        if (!(e instanceof ProcessingException)) {
            ArrayNode trace = update.arrayNode();
            e.printStackTrace(new StackTraceCapture(trace));
            ObjectNode result = (ObjectNode)update.get("result");
            result.set("trace", trace);
        }
        return update;
    }

    private ObjectNode passwordReport(ArrayNode passwords) {
        // create an object like:
        //   result:
        //     status: success
        //     message: generated passwords
        //   passwords:
        //   - alias: authenticator
        //     username: username
        //     email: email
        //     password: encrypted password
        ObjectNode passwordReport = mapper.createObjectNode();
        passwordReport.set("passwords", passwords);
        return insertResult(passwordReport, true, "generated passwords");
    }

	/*- add processors -------------------------------------------------------*/

    private ObjectNode processAddUser(ObjectNode entry, ObjectNode actions, ArrayNode results, ArrayNode passwords) throws Exception {
        // get or create the authenticator identified by "alias"
        String alias = REST.asText(entry.remove("alias"));
        if (alias == null) {
            throw new ProcessingException("\"alias\" (authenticator alias) required when adding a user");
        }
        ObjectNode authenticator = getAuthenticator(alias);
        if (authenticator == null) {
            authenticator = createAuthenticatorFromTemplate(alias);
            results.add(insertResult(authenticator, true, String.format("created authenticator %s with default template", alias)));
        }
        // Create user
        if (options.contains(Option.generatePass)) {
            generatePasswordForUser(entry);
        }
        ObjectNode user = api.createUser(entry, authenticator);
        if (user == null) {
            throw new ProcessingException("user not created");
        }
        if (options.contains(Option.generatePass)) {
            passwords.add(generatedPassword(alias, entry));
        }
        if (actions != null) {
            createActions(actions, user);
        }
        results.add(insertResult(user, true, String.format("created %s", REST.getSubElementAsText(user, "username"))));
        return user;
    }

    private ObjectNode processAddAuthenticator(ObjectNode entry, ObjectNode actions, ArrayNode results) throws Exception {
        ObjectNode authenticator = api.createAuthenticator(entry);
        if (authenticator == null) {
            throw new ProcessingException("error: authenticator not created");
        }
        if (actions != null) {
            createActions(actions, authenticator);
        }
        results.add(insertResult(authenticator, true, String.format("created %s",
                REST.getSubElementAsText(authenticator, "alias"))));
        return authenticator;
    }

    private ObjectNode processAddConnection(ObjectNode entry, ObjectNode actions, ArrayNode results) throws Exception {
        ObjectNode connection = api.createConnection(entry);
        if (connection == null) {
            throw new ProcessingException("error: connection not created");
        }
        api.deleteActions(connection);
        if (actions != null) {
            createActions(actions, connection);
        }
        results.add(insertResult(connection, true, String.format("created %s",
                REST.getSubElementAsText(connection, "alias"))));
        return connection;
    }

    private ObjectNode processAdd(ObjectNode entry, ObjectNode actions, ArrayNode results, ArrayNode passwords) throws Exception {
        String type = REST.getSubElementAsText(entry, "type", "user");
        if (type.equals("user")) {
            entry.remove("type");
            return processAddUser(entry, actions, results, passwords);
        } else if (AUTH_TYPES.contains(type)) {
            return processAddAuthenticator(entry, actions, results);
        } else {
            return processAddConnection(entry, actions, results);
        }
    }

	/*- list processors ------------------------------------------------------*/

    private ObjectNode processListUser(ObjectNode entry, ArrayNode results, Operation operation) throws Exception {
        String username = REST.getSubElementAsText(entry, "username");
        if (username == null) {
            throw new ProcessingException("\"username\" not found");
        }
        ObjectNode result = listUser(username);
        String message = String.format("%s user %s", operation.tag(), username);
        results.add(insertResult(result, true, message));
        return result;
    }

    private ObjectNode processListAuthenticator(ObjectNode entry, ArrayNode results, Operation operation, boolean includeUsers) throws Exception {
        String alias = REST.getSubElementAsText(entry, "alias");
        if (alias == null) {
            throw new ProcessingException("\"alias\" not found");
        }
        List<ObjectNode> result = listAuthenticator(alias, includeUsers);
        String message = includeUsers ? String.format("%s authenticator %s", operation.tag(), alias)
                                      : String.format("%s authenticator %s with %d users", operation.tag(), alias, result.size()-1);
        results.add(insertResult(result.get(0), true, message));
        for (int i=1; i<result.size(); i++) {
            message = String.format("%s authenticator %s: user %d of %d", operation.tag(), alias, i, result.size()-1);
            results.add(insertResult(result.get(i), true, message));
        }
        return result.get(0);
    }

    private ObjectNode processListConnection(ObjectNode entry, ArrayNode results, Operation operation) throws Exception {
        String alias = REST.getSubElementAsText(entry, "alias");
        if (alias == null) {
            throw new ProcessingException("\"alias\" not found");
        }
        ObjectNode result = listConnection(alias);
        String message = String.format("%s connection %s", operation.tag(), alias);
        results.add(insertResult(result, true, message));
        return result;
    }

    private ObjectNode processList(ObjectNode entry, ArrayNode results, Operation operation) throws Exception {
        String type = REST.getSubElementAsText(entry, "type", "user");
        if (type.equals("user")) {
            return processListUser(entry, results, operation);
        } else if (AUTH_TYPES.contains(type)) {
            return processListAuthenticator(entry, results, operation,
                    operation.equals(Operation.list) || operation.equals(Operation.delete));
        } else {
            return processListConnection(entry, results, operation);
        }
    }

	/*- main file processor --------------------------------------------------*/

    private ArrayNode prepareFile(String fn) throws Exception {
        // load file content into a string
        String content = fn.equals("-")
                ? new String(ByteStreams.toByteArray(System.in))
                : new String(Files.readAllBytes(Paths.get(fn)));
        ArrayNode file = null;

        // Option 1: try to load it as a JSON or YAML file
        try {
            JsonNode json = mapper.readTree(content);
            // file is a list of entries to process:
            //   convert a single entry file into a list of one
            if (!json.isArray()) {
                file = mapper.createArrayNode();
                file.add(json);
            } else {
                file = (ArrayNode)json;
                json = file.get(0);
            }
            // now file is an array and json is the first element: test it
            if (json.isObject()) {
                return file;
            }
        } catch (Exception notjson) {
            // try something else
        }
        file = null;

        // Option 2: see if it can be loaded as CSV
        //try {
            file = ConvertCSVToHarmonyJSON.parseCSVFile(content);
        //} catch (Exception e) {
            //throw new ProcessingException(e.getMessage());
        //}
        return file;
    }

    public void processFiles(String[] fns) throws IOException {
        ArrayNode results = mapper.createArrayNode();
        ArrayNode passwords = mapper.createArrayNode();

        for (String fn : fns) {
            try {
                ArrayNode file = prepareFile(fn);
                processFile(file, results, passwords);
            } catch (ProcessingException e) {
                results.add(insertResult(REST.setSubElement(null, "result.file", fn), false, e.getMessage()));
            } catch (Exception e) {
                results.add(insertResult(REST.setSubElement(null, "result.file", fn), false, e));
            }
        }

        cleanup(results);
        if (passwords.size() > 0) {
            results.add(passwordReport(passwords));
        }
        mapper.writeValue(System.out, results);
    }

    public void processFile(ArrayNode file, ArrayNode results, ArrayNode passwords) {
        Iterator<JsonNode> elements = file.elements();
        while (elements.hasNext()) {
            ObjectNode entry = (ObjectNode)elements.next();
            ObjectNode original = entry.deepCopy();
            try {
                Operation operation = Operation.valueOf(REST.asText(entry.remove("operation"),
                        options.contains(Option.update) ? "update" : "add"));
                // collect actions into an ObjectNode
                ObjectNode actions = normalizeActions(entry.remove("actions"));
                switch (operation) {
                case add:
                    processAdd(entry, actions, results, passwords);
                    break;
                case list:
                    processList(entry, results, operation);
                    break;
                case update:
                    {
                        ArrayNode tempResults = entry.arrayNode();
                        ObjectNode toUpdate = processList(entry, tempResults, operation);
                        ObjectNode updated = updateResource(toUpdate, entry);
                        if (actions != null) {
                            createActions(actions, updated);
                        }
                        results.addAll(tempResults);
                        results.add(insertResult(updated, true, String.format("%s updated",
                                getAliasOrUsername(updated))));
                    }
                    break;
                case delete:
                    {
                        ArrayNode tempResults = entry.arrayNode();
                        ObjectNode toDelete = processList(entry, tempResults, operation);
                        api.delete(toDelete);
                        results.addAll(tempResults);
                    }
                    break;
                default:
                    throw new ProcessingException("operation "+operation+" not supported");
                }
            } catch (Exception e) {
                results.add(insertResult(original, false, e));
            }
        }
    }

    public VersalexRestBatchProcessor(REST api) {
        this (api, EnumSet.noneOf(Option.class));
    }

    public VersalexRestBatchProcessor(REST api, EnumSet<Option> options) {
        this.api = api;
        this.options = options;
        this.exportPassword = null;
    }
}
