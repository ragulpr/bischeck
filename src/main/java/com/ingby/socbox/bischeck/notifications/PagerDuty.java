package com.ingby.socbox.bischeck.notifications;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.util.JSONBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.configuration.ConfigurationException;
import com.ingby.socbox.bischeck.configuration.ConfigurationManager;
import com.ingby.socbox.bischeck.host.Host;
import com.ingby.socbox.bischeck.servers.MessageServerInf;
import com.ingby.socbox.bischeck.service.JDBCService;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceStateInf;
import com.ingby.socbox.bischeck.serviceitem.SQLServiceItem;
import com.ingby.socbox.bischeck.threshold.DummyThreshold;
import com.ingby.socbox.bischeck.threshold.Threshold;
import com.ingby.socbox.bischeck.threshold.Threshold.NAGIOSSTAT;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.core.TimerContext;

public final class PagerDuty implements Notifier, MessageServerInf {
	private final static Logger LOGGER = LoggerFactory.getLogger(PagerDuty.class);

	private static HashMap<String, PagerDuty> notificator = new HashMap<String,PagerDuty>();

	private String serviceKey;
	private int connectionTimeout;
	private URL url;
	private final String instanceName;
	private boolean sendMessage;
	private ServiceKeyRouter skr;

	private String defaultServiceKey;

	public static void main(final String[] args) throws ConfigurationException {
		ConfigurationManager confMgmr;
		try {
			confMgmr = ConfigurationManager.getInstance();
		} catch (java.lang.IllegalStateException e) {
			System.setProperty("bishome", ".");
			System.setProperty("xmlconfigdir","etc");

			ConfigurationManager.init();
			confMgmr = ConfigurationManager.getInstance();  
		}

		Host host = new Host("sqlHost");
		JDBCService jdbcService = new JDBCService("sqlService",null);
		// Set faulty driver url -> connection will fail
		jdbcService.setConnectionUrl("jdbc:derby:memoryNOTEXISTS:myDB;create=true");
		jdbcService.setDriverClassName("org.apache.derby.jdbc.EmbeddedDriver");
		jdbcService.setLevel(NAGIOSSTAT.CRITICAL);


		SQLServiceItem sqlServiceItem = new SQLServiceItem("sqlItem");
		sqlServiceItem.setService(jdbcService);
		sqlServiceItem.setThresholdClassName("DummyThreshold");
		Threshold threshold = new DummyThreshold("sqlHost", "jdbc", "sql");
		sqlServiceItem.setThreshold(threshold);


		host.addService(jdbcService);
		jdbcService.setHost(host);
		jdbcService.addServiceItem(sqlServiceItem);

		PagerDuty pd = PagerDuty.getInstance("Test");
		
			pd.sendAlert(jdbcService.getNotificationData());
			//pd.sendResolve(jdbcService.getNotificationData());
		
	}


	synchronized public static PagerDuty getInstance(String name) {

		if (!notificator.containsKey(name) ) {
			notificator.put(name,new PagerDuty(name));
		}
		return notificator.get(name);
	}

		
	private PagerDuty(String name) {

		instanceName = name;
		Properties defaultproperties = getNotificationProperties();
		Properties prop = ConfigurationManager.getInstance().getServerProperiesByName(name);

		final String urlName = prop.getProperty("url",
				defaultproperties.getProperty("url"));

		try {
			url = new URL(urlName);
		} catch (MalformedURLException e) {
			LOGGER.error("{} - The url {} is not correctly formated", instanceName, urlName, e);
			throw new IllegalArgumentException(e);
		}

		serviceKey = prop.getProperty("service_key", 
				defaultproperties.getProperty("service_key"));
		
		defaultServiceKey = prop.getProperty("default_service_key");
		
		skr = new ServiceKeyRouter(serviceKey,defaultServiceKey);

		connectionTimeout = Integer.parseInt(prop.getProperty("connectionTimeout",
				defaultproperties.getProperty("connectionTimeout")));

		sendMessage = Boolean.parseBoolean(prop.getProperty("send", 
				defaultproperties.getProperty("send")));
		
	}

	
	/**
	 * Return properties with default values
	 * @return default properties
	 */
	public static Properties getNotificationProperties() {
		Properties defaultproperties = new Properties();

		defaultproperties.setProperty("url","https://events.pagerduty.com/generic/2010-04-15/create_event.json");
		defaultproperties.setProperty("service_key","Sign up to get your own");
		defaultproperties.setProperty("default_service_key","");
		defaultproperties.setProperty("connectionTimeout","5000");
		defaultproperties.setProperty("send","true");

		return defaultproperties;
	}

	
	/**
     * Unregister the server and its configuration
     * @param name of the server instance
     */
    synchronized public static void unregister(final String name) {
    	notificator.remove(name);
    }

	public void sendAlert(Map<String, String> notificationData) {
		Writer message = new StringWriter();

		final String key = skr.getServiceKey(notificationData.get("host"),notificationData.get("service"));
		if (key == null) {
			LOGGER.error("Service for {} do not have a service key defined. Will not be sent by instance {}.", 
					Util.fullQouteHostServiceName(notificationData.get("host"),notificationData.get("service")),
					instanceName);
			return;
		}
		new JSONBuilder(message)
		.object()
		.key("service_key")
		.value(key)
		.key("incident_key")
		.value(notificationData.get("incident_key"))
		.key("event_type")
		.value("trigger")
		.key("description")
		.value(notificationData.get("description"))
		.key("details")
		.object()
		.key("servicedef")
		.value(notificationData.get("host") + "-" + notificationData.get("service"))
		.key("state")
		.value(notificationData.get("state"))
		.endObject()
		.endObject();
		
		JSONObject json = null;
		try {
			if (sendMessage) {
				json = sendMessage(message);
			}
		} catch (IOException e) {
			LOGGER.error("Sedning trigger message to {} failed.", instanceName, e);
		}

		LOGGER.info("Alert message to {} : {}", instanceName, message.toString());
		
		success(json, notificationData.get("incident_key"));

	}


	public void sendResolve(Map<String, String> notificationData) {
		Writer message = new StringWriter();
		
		String key = skr.getServiceKey(notificationData.get("host"),notificationData.get("service"));
		if (key == null) {
			LOGGER.error("Service for {} do not have a service key defined. Will not be sent by instance {}.", 
					Util.fullQouteHostServiceName(notificationData.get("host"),notificationData.get("service")),
					instanceName);
			return;
		}
		new JSONBuilder(message)
		.object()
		.key("service_key")
		.value(key)
		.key("incident_key")
		.value(notificationData.get("incident_key"))
		.key("event_type")
		.value("resolve")
		.key("description")
		.value(notificationData.get("description")).endObject();

		
		JSONObject json = null;
		try {
			if (sendMessage) {
				json = sendMessage(message);
			}
		} catch (IOException e) {
			LOGGER.error("Sedning resolve message to {} failed.", instanceName, e);
		}
		LOGGER.info("Resolve message to {} : {}", instanceName, message.toString());
		
		success(json, notificationData.get("incident_key"));
	}


	private JSONObject sendMessage(final Writer message) throws IOException {
		String payload = message.toString();
		HttpURLConnection conn = null;

		final String timerName = instanceName+"_sendTimer";

		final Timer timer = Metrics.newTimer(PagerDuty.class, 
				timerName , TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
		final TimerContext context = timer.time();

		JSONObject json = null;

		try {
			conn = createHTTPConnection(payload);
	

		if (!postHTTP(conn, payload)) {
			return null;
		}

		json = responseHTTP(conn);		

		} finally {
			long duration = context.stop()/1000000;
			LOGGER.debug("PagerDuty for {} send execute: {} ms", instanceName, duration);
		}

		return json;

	}

	private boolean success(JSONObject json, String incidentKey) {
		if (json != null) {
			
			if ( "success".equals(json.getString("status")) && incidentKey.equals(json.getString("incident_key"))) {
				return true;
			}
			
			if (!incidentKey.equals(json.getString("incident_key"))) {
				LOGGER.error("Response incident key, {}, do not match expected, {} for instance {}", 
						json.getString("incident_key"), incidentKey, instanceName);
				return false;
			}
		}
		return false;
	}

	
	private JSONObject responseHTTP(final HttpURLConnection conn) throws IOException {
		BufferedReader br = null;
		StringBuilder sb = null;
		
		try {
			br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			sb = new StringBuilder();

			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			} 
		} finally {
			if(br != null) {
				try {
					br.close();
				} catch (IOException ignore) {}
			}
		}
		
		JSONObject json = null;
		if (sb.toString().isEmpty()) {
			LOGGER.error("HTTPS response for instance {} returned no data", instanceName);
		} else {
			json = (JSONObject) JSONSerializer.toJSON(sb.toString());
			if (json == null ) {
				LOGGER.error("PagerDuty returned null json object for message {} for instance {}", sb.toString(), instanceName);
			} else if (json.size() != 3 || !json.has("status") || !json.has("incident_key")) {
				LOGGER.error("PagerDuty returned faulty json message for {} for instance {}", sb.toString(), instanceName);
			}
		}
		
		return json;
	}

	
	private boolean postHTTP(HttpURLConnection conn, final String payload) throws IOException {
		OutputStreamWriter wr = null;

		try {
			wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(payload);
			wr.flush();
			int returnStatus = conn.getResponseCode();
			if (returnStatus != HttpURLConnection.HTTP_OK) {
				if (returnStatus == HttpURLConnection.HTTP_BAD_REQUEST) {
					LOGGER.error("PagerDuty responded with {} for instance {}, check Bischeck configuration", 
							instanceName, returnStatus);
					return false;
				} else if (returnStatus == HttpURLConnection.HTTP_FORBIDDEN) {
					LOGGER.error("PagerDuty responded with {} for instance {}, probably making to many API calls to PagerDuty", 
							instanceName, returnStatus);
					return false;
				} else if (returnStatus >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
					LOGGER.error("PagerDuty responded with {} for instance {}, check PagerDuty server status and vaildate your account settings", 
							instanceName, returnStatus);
					return false;
				}
			}
		} finally {
			if (wr != null) {
				try {
					wr.close();
				} catch (IOException ignore) {}
			}
		}
		return true;
	}


	private HttpURLConnection createHTTPConnection(final String payload)
			throws IOException {

		LOGGER.debug("{} - Message: {}", instanceName, payload);
		HttpsURLConnection conn;

		conn = (HttpsURLConnection) url.openConnection();

		conn.setDoOutput(true);

		conn.setRequestMethod("POST");

		conn.setConnectTimeout(connectionTimeout);
		conn.setRequestProperty("Content-Length", "" + 
				Integer.toString(payload.getBytes().length));

		conn.setRequestProperty("User-Agent", "bischeck");
		conn.setRequestProperty("Content-Type","application/json");
		conn.setRequestProperty("Accept","text/html,application/xhtml+xml,application/xml");
		conn.setRequestProperty("Accept-Language","en-US,en;q=0.8");
		conn.setRequestProperty("Accept-Charset","ISO-8859-1,utf-8");
		LOGGER.debug("Open connection for instance {} : {}", instanceName, conn.toString());
		return conn;
	}

	@Override
	public void onMessage(Service message) {
		if ( ((ServiceStateInf) message).getServiceState().isResolved() ) {
			sendResolve(message.getNotificationData());
		} else {
			sendAlert(message.getNotificationData());
		}
	}

	@Override
	public String getInstanceName() {
		return instanceName;
	}

	@Override
	public void unregister() {
		// TODO Auto-generated method stub
		
	}

}