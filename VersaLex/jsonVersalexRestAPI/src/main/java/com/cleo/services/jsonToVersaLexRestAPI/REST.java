package com.cleo.services.jsonToVersaLexRestAPI;

import java.util.Arrays;
import java.util.Iterator;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Strings;

public class REST {

	public static String API_BASE = "/api";
	public static String AUTHENTICATION_URL = API_BASE+"/authentication";
	public static String CONNECTIONS_URL = API_BASE+"/connections";
	public static String AUTHENTICATORS_URL = API_BASE+"/authenticators";
	public static String ACTIONS_URL = API_BASE+"/actions";

	private String baseUrl;
	private String authToken;

	private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

	public REST(String url, int port, String username, String password) throws Exception {
		this.baseUrl = url + ":" + port;
		this.authToken = authorize(username, password);
	}

	private static HttpClient defaultHTTPClient = null;

	public static HttpClient getDefaultHTTPClient() {
		if (defaultHTTPClient == null) {
			defaultHTTPClient = HttpClients.custom()
					.setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).build())
					.build();
		}
		return defaultHTTPClient;
	}

	/*------------------------------------------------------------------------*
	 * Authorization                                                          *
	 *------------------------------------------------------------------------*/

	private String authorize(String username, String password) throws Exception {
		HttpPost post = new HttpPost(this.baseUrl + AUTHENTICATION_URL);
		post.addHeader("content-type", "application/x-www-form-urlencoded");
		post.setEntity(new UrlEncodedFormEntity(Arrays.asList(new BasicNameValuePair("grant_type", "password"),
				new BasicNameValuePair("username", username), new BasicNameValuePair("password", password))));

		ObjectNode result = execute(post, 200);
		return result.get("access_token").asText();
	}

	public String getAuthToken() {
		return this.authToken;
	}

	/*------------------------------------------------------------------------*
	 * Basic HTTP Operations                                                  *
	 *------------------------------------------------------------------------*/

	private ObjectNode execute(HttpRequestBase request, int successCode) throws Exception {
		HttpClient client = REST.getDefaultHTTPClient();
		if (this.authToken != null) {
			request.addHeader("Authorization", "Bearer " + this.authToken);
		}
		try {
			HttpResponse response = client.execute(request);
			int code = response.getStatusLine().getStatusCode();
			String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
			if (code == successCode) {
				return body==null ? null : (ObjectNode) mapper.readTree(body);
			} else {
				String message = String.format("Failed HTTP Request (%d)", code);
				if (body != null) {
                    message += ": " + getSubElementAsText(mapper.readTree(body), "message");
				}
				throw new ProcessingException(message);
			}
		} finally {
			request.reset();
		}
	}

	public ObjectNode get(JsonNode object) throws Exception {
	    return get(getHref(object));
	}

	public ObjectNode get(String href) throws Exception {
		HttpGet get = new HttpGet(this.baseUrl + href);
		return execute(get, 200);
	}

	public ObjectNode post(JsonNode json, JsonNode object) throws Exception {
	    return post(json, getHref(object));
	}

	public ObjectNode post(JsonNode json, String href) throws Exception {
		HttpPost post = new HttpPost(this.baseUrl + href);
		post.setEntity(new StringEntity(json.toString()));
		post.addHeader("content-type", "application/json");
		return execute(post, 201);
	}

	public ObjectNode put(JsonNode json, JsonNode object) throws Exception {
	    return put(json, getHref(object));
	}

	public ObjectNode put(JsonNode json, String href) throws Exception {
		HttpPut put = new HttpPut(this.baseUrl + href);
		put.setEntity(new StringEntity(json.toString()));
		put.addHeader("content-type", "application/json");
		return execute(put, 200);
	}

	public void delete(JsonNode object) throws Exception {
	    delete(getHref(object));
	}

	public void delete(String href) throws Exception {
		HttpDelete delete = new HttpDelete(this.baseUrl + href);
        execute(delete, 204);
	}

	/*------------------------------------------------------------------------*
	 * VersaLex API Collections                                               *
	 *------------------------------------------------------------------------*/

	public class JsonCollection implements Iterator<ObjectNode> {
		private String path;
		private String filter;
		private HttpClient httpClient = getDefaultHTTPClient();
		private int totalResults = -1;
		private int startIndex;
		private ArrayNode resources;
		private int index;
		private Exception exception = null;

		public JsonCollection(String path) {
			this(path, null);
		}

		public JsonCollection(String path, String filter) {
			this.path = path;
			this.filter = filter;
			startIndex = 0;
			fill();
		}

		private void fill() {
			try {
				URIBuilder uri = new URIBuilder(baseUrl + path).addParameter("startIndex", String.valueOf(startIndex));
				if (!Strings.isNullOrEmpty(filter)) {
					uri.addParameter("filter", filter);
				}
				uri.addParameter("count", "2");
				HttpGet httpGet = new HttpGet(uri.build());
				if (authToken != null) {
					httpGet.addHeader("Authorization", "Bearer " + authToken);
				}
				HttpResponse response = httpClient.execute(httpGet);
				int responseCode = response.getStatusLine().getStatusCode();
				String responseBody = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity());
				if (responseCode != 200) {
					String msg = String.format("Failed HTTP Request (%d)", responseCode);
					if (responseBody != null) {
						try {
							msg += ": " + ((ObjectNode) mapper.readTree(EntityUtils.toString(response.getEntity()))
									.get("message")).asText();
						} catch (Exception e) {
							// leave it just as a code
						}
					}
					throw new Exception(msg);
				}
				ObjectNode responseJson = (ObjectNode) mapper.readTree(responseBody);
				totalResults = Integer.valueOf(getSubElementAsText(responseJson, "totalResults", "0"));
				resources = (ArrayNode) responseJson.get("resources");
				startIndex += resources.size();
				index = 0;
			} catch (Exception e) {
				exception = e;
			}
		}

		@Override
		public boolean hasNext() {
			if (exception == null && totalResults > 0 && index >= resources.size() && startIndex < totalResults) {
				fill();
			}
			return exception == null && totalResults > 0 && index < resources.size();
		}

		@Override
		public ObjectNode next() {
			if (!hasNext()) {
				return null;
			}
			return (ObjectNode) resources.get(index++);
		}

		public int totalResults() {
		    return this.totalResults;
		}

		public Exception exception() {
			return this.exception;
		}

		public void throwException() throws Exception {
			if (exception != null) {
				throw exception;
			}
		}
	}

	/*------------------------------------------------------------------------*
	 * Specific Resource Operations                                           *
	 *------------------------------------------------------------------------*/

	public ObjectNode createConnection(JsonNode connectionJson) throws Exception {
		return post(connectionJson, CONNECTIONS_URL);
	}

	public ObjectNode createAuthenticator(JsonNode authenticatorJson) throws Exception {
		return post(authenticatorJson, AUTHENTICATORS_URL);
	}

	public ObjectNode createUser(ObjectNode userJson, ObjectNode authenticator) throws Exception {
		return post(userJson, getSubElementAsText(authenticator, "_links.users.href"));
	}

	public ObjectNode createAction(JsonNode actionJson) throws Exception {
		return post(actionJson, ACTIONS_URL);
	}

	public ObjectNode getResource(String resourcePath, String keyAttribute, String key) throws Exception {
		JsonCollection resources = new JsonCollection("/api/" + resourcePath, keyAttribute + " eq \"" + key + "\"");
		if (resources.hasNext()) {
			ObjectNode resource = resources.next();
			return resource;
		}
		resources.throwException();
		return null;
	}

	public ObjectNode getAuthenticator(String alias) throws Exception {
		return getResource("authenticators", "alias", alias);
	}

	public ObjectNode getUser(String username) throws Exception {
		JsonCollection authenticators = new JsonCollection("/api/authenticators");
		while (authenticators.hasNext()) {
			ObjectNode authenticator = authenticators.next();
			JsonCollection users = new JsonCollection(REST.getSubElementAsText(authenticator, "_links.users.href"),
					"username eq \"" + username + "\"");
			if (users.hasNext()) {
				ObjectNode user = users.next();
				return user;
			} else if (users.exception() != null) {
				throw users.exception();
			}
		}
		return null;
	}

	public String getUserHref(String username) throws Exception {
		return REST.getSubElementAsText(getUser(username), "_links.self.href");
	}

	public ObjectNode getConnection(String alias) throws Exception {
		return getResource("connections", "alias", alias);
	}

	public String getConnectionHref(String alias) throws Exception {
		return REST.getSubElementAsText(getConnection(alias), "_links.self.href");
	}

	public void deleteActions(ObjectNode connection) throws Exception {
		Iterator<JsonNode> actions = connection.get("_links").get("actions").elements();
		while (actions.hasNext()) {
			JsonNode action = actions.next();
			delete(getSubElementAsText(action, "href"));
		}
	}

	/*------------------------------------------------------------------------*
	 * JSON Tree Manipulators                                                 *
	 *------------------------------------------------------------------------*/

	public static JsonNode getSubElement(JsonNode node, String jsonKey) {
		for (String key : jsonKey.split("\\.")) {
			if (node != null) {
				node = node.get(key);
			}
		}
		return node;
	}

	public static String getSubElementAsText(JsonNode node, String jsonKey, String ifNull) {
		JsonNode subElement = getSubElement(node, jsonKey);
		return subElement == null ? ifNull : subElement.asText();
	}

	public static String getSubElementAsText(JsonNode node, String jsonKey) {
		return getSubElementAsText(node, jsonKey, null);
	}

	public static String getHref(JsonNode node) {
	    return getSubElementAsText(node, "_links.self.href");
	}

	public static String asText(JsonNode node) {
	    return node == null ? null : node.asText();
	}

	public static String asText(JsonNode node, String ifNull) {
	    return node == null ? ifNull : node.asText(ifNull);
	}

	public static ObjectNode walkToSubElement(ObjectNode node, String[] key) {
	    ObjectNode current = node;
	    for (int i=0; i<key.length-1; i++) {
	        JsonNode test = current.path(key[i]);
	        if (test.isObject()) {
	            current = (ObjectNode)test;
	        } else {
	            current = node.putObject(key[i]);
	        }
	    }
	    return current;
	}

	public static ObjectNode setSubElement(ObjectNode node, String jsonKey, String value) {
	    if (!Strings.isNullOrEmpty(value)) {
	        if (node == null) {
	            node = mapper.createObjectNode();
	        }
            String[] key = jsonKey.split("\\.");
            walkToSubElement(node, key).put(key[key.length-1], value);
	    }
        return node;
	}

	public static ObjectNode setSubElement(ObjectNode node, String jsonKey, Integer value) {
	    if (value != null) {
	        if (node == null) {
	            node = mapper.createObjectNode();
	        }
            String[] key = jsonKey.split("\\.");
            walkToSubElement(node, key).put(key[key.length-1], value);
	    }
        return node;
	}

	public static ObjectNode setSubElement(ObjectNode node, String jsonKey, Boolean value) {
	    if (value != null) {
	        if (node == null) {
	            node = mapper.createObjectNode();
	        }
            String[] key = jsonKey.split("\\.");
            walkToSubElement(node, key).put(key[key.length-1], value);
	    }
        return node;
	}

    public static ObjectNode setSubElement(ObjectNode node, String jsonKey, JsonNode value) {
        if (value != null) {
            if (node == null) {
                node = mapper.createObjectNode();
            }
            String[] key = jsonKey.split("\\.");
            walkToSubElement(node, key).replace(key[key.length-1], value);
        }
        return node;
    }

	public static ObjectNode removeElements(ObjectNode node, String...elements) {
	    for (String element : elements) {
	        String[] kv = element.split("=", 2);
	        String[] keys = kv[0].split("\\.");
	        JsonNode value;
	        try {
                value = kv.length > 1 ? mapper.readTree(kv[1]) : null;
            } catch (Exception e) {
                // swallow this exception since the "elements" are hard-coded, not up to the user
                throw new RuntimeException(e);
            }
	        // walk down to the targeted node
	        ObjectNode subnode = node;
	        ObjectNode[] parent = new ObjectNode[keys.length];
	        for (int i=0; i < keys.length-1 && subnode != null; i++) {
	            JsonNode test = subnode.get(keys[i]);
	            if (test != null && test.isObject()) {
	                parent[i] = subnode;
	                subnode = (ObjectNode)test;
	            } else {
	                subnode = null;
	            }
	        }
	        // if found, remove it and walk back up removing empty objects
	        if (subnode != null) {
	            if (value == null || value.equals(subnode.get(keys[keys.length-1]))) {
                    subnode.remove(keys[keys.length-1]);
	                for (int i=keys.length-2; !subnode.elements().hasNext() && i >= 0; i--) {
                        subnode = parent[i];
                        subnode.remove(keys[i]);
                    }
	            }
	        }
	    }
	    return node;
	}

}
