/*
#
# Copyright (C) 2010-2013 Anders Håål, Ingenjorsbyn AB
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
 */
package com.ingby.socbox.bischeck.servers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.BlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.codahale.metrics.Timer;
import com.ingby.socbox.bischeck.NagiosUtil;
import com.ingby.socbox.bischeck.monitoring.MetricsManager;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceTO;
import com.ingby.socbox.bischeck.threshold.Threshold.NAGIOSSTAT;

public class NRDPWorker implements WorkerInf, Runnable {
    private final static Logger LOGGER = LoggerFactory
            .getLogger(NRDPWorker.class);
    private String urlstr;
    private String cmd;
    private NagiosUtil nagutil;
    private String instanceName;
    private BlockingQueue<ServiceTO> bq;
    private ServerCircuitBreak circuitBreak;
    private URL url;
    private Integer connectionTimeout;

    public NRDPWorker(String instanceName, BlockingQueue<ServiceTO> bq,
            ServerCircuitBreak circuitBreak, String urlstr, String cmd,
            Integer connectionTimeout) {

        this.nagutil = new NagiosUtil();
        this.urlstr = urlstr;
        this.cmd = cmd;
        this.instanceName = instanceName;
        this.bq = bq;
        this.circuitBreak = circuitBreak;
        this.connectionTimeout = connectionTimeout;
        try {
            url = new URL(urlstr);
        } catch (MalformedURLException e) {
            LOGGER.error("{} - The url {} is not correctly formated",
                    instanceName, urlstr, e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * The thread will run until MAX_RUNS_BEFORE_END*Math.random() iterations
     * before ends. This is to support a dynamic scaling on threads since the
     * number of threads is controlled by the
     * {@link NSCAServer#onMessage(Service)}
     */
    @Override
    public void run() {
        int runCount = (int) (Math.random() * MAX_RUNS_BEFORE_END);
        LOGGER.debug("{} - Worker count {}", instanceName, runCount);

        while (runCount > 0) {
            ServiceTO serviceTo = null;
            try {
                serviceTo = bq.take();
            } catch (InterruptedException e1) {
                LOGGER.info("{} - Worker thread is interupted", instanceName);
                break;
            }

            circuitBreak.execute(this, serviceTo);

            runCount--;
        }
        LOGGER.debug("{} -Thread {} going out of service ", instanceName,
                Thread.currentThread().getName());
    }

    @Override
    public void send(ServiceTO serviceTo) throws ServerException {

        NAGIOSSTAT level = serviceTo.getLevel();
        String xml = xmlNRDPFormat(level, serviceTo.getHostName(),
                serviceTo.getServiceName(),
                nagutil.createNagiosMessage(serviceTo));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(ServerUtil.logFormat(instanceName, serviceTo, xml));
        }

        connectAndSend(xml);
    }

    private void connectAndSend(String xml) throws ServerException {

        final String timerName = instanceName + "_sendTimer";
        final Timer timer = MetricsManager
                .getTimer(NRDPServer.class, timerName);
        final Timer.Context context = timer.time();

        HttpURLConnection conn = null;
        OutputStreamWriter wr = null;

        try {
            LOGGER.debug("{} - Url: {}", instanceName, urlstr);
            String payload = cmd + xml;
            conn = createHTTPConnection(payload);
            wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(payload);
            wr.flush();

            /*
             * Look for status != 0 by building a DOM to parse
             * <status>0</status> <message>OK</message>
             */

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder dBuilder = null;
            try {
                dBuilder = dbFactory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                LOGGER.error("{} - Could not get a doc builder", instanceName,
                        e);
                return;
            }

            /*
             * Getting the value for status and message tags
             */
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getInputStream()));) {

                StringBuilder sb = new StringBuilder();

                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                InputStream is = new ByteArrayInputStream(sb.toString()
                        .getBytes("UTF-8"));
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("NRDP return string - {}",
                            convertStreamToString(is));
                    is.reset();
                }

                Document doc = null;

                doc = dBuilder.parse(is);

                doc.getDocumentElement().normalize();
                String rootNode = doc.getDocumentElement().getNodeName();
                NodeList responselist = doc.getElementsByTagName(rootNode);
                String result = (String) ((Element) responselist.item(0))
                        .getElementsByTagName("status").item(0).getChildNodes()
                        .item(0).getNodeValue().trim();

                LOGGER.debug("NRDP return status is: {}", result);

                if (!"0".equals(result)) {
                    String message = (String) ((Element) responselist.item(0))
                            .getElementsByTagName("message").item(0)
                            .getChildNodes().item(0).getNodeValue().trim();
                    LOGGER.error(
                            "{} - nrdp returned message \"{}\" for xml: {}",
                            instanceName, message, xml);
                }
            } catch (SAXException e) {
                LOGGER.error("{} - Could not parse response xml", instanceName,
                        e);
            }

        } catch (IOException e) {
            LOGGER.error(
                    "{} - Network error - check nrdp server and that service is started",
                    instanceName, e);
            throw new ServerException(e);
        } finally {
            long duration = context.stop() / 1000000;
            LOGGER.debug("{} - Nrdp send execute: {} ms", instanceName,
                    duration);
            if (wr != null) {
                try {
                    wr.close();
                } catch (IOException ignore) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private HttpURLConnection createHTTPConnection(String payload)
            throws IOException {

        LOGGER.debug("{} - Message: {}", instanceName, payload);
        HttpURLConnection conn;

        conn = (HttpURLConnection) url.openConnection();

        conn.setDoOutput(true);

        conn.setRequestMethod("POST");

        conn.setConnectTimeout(connectionTimeout);
        conn.setRequestProperty("Content-Length",
                "" + Integer.toString(payload.getBytes().length));

        conn.setRequestProperty("User-Agent", "bischeck");
        conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/xml");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        conn.setRequestProperty("Accept-Charset", "ISO-8859-1,utf-8");
        return conn;
    }

    /**
     * Format the xml to be sent
     * 
     * @param level
     *            the Nagios state level
     * @param hostname
     * @param servicename
     * @param serviceOutput
     *            the performance string
     * @return the formated xml
     */
    private String xmlNRDPFormat(NAGIOSSTAT level, String hostname,
            String servicename, String serviceOutput) {
        StringBuilder strbuf = new StringBuilder();

        // Check encoding and character set and how it works out
        strbuf.append("<?xml version='1.0' encoding='utf-8'?>");
        strbuf.append("<checkresults>");
        strbuf.append("<checkresult type='service'>");
        strbuf.append("<hostname>").append(hostname).append("</hostname>");
        strbuf.append("<servicename>").append(servicename)
                .append("</servicename>");
        strbuf.append("<state>").append(level.val()).append("</state>");
        strbuf.append("<output>")
                .append(StringEscapeUtils.escapeHtml(serviceOutput))
                .append("</output>");
        strbuf.append("</checkresult>");
        strbuf.append("</checkresults>");

        String utfenc = null;
        try {
            utfenc = URLEncoder.encode(strbuf.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("{} - Unsupported encoding of xml: {}", instanceName,
                    strbuf.toString(), e);
        }
        return utfenc;
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

}
