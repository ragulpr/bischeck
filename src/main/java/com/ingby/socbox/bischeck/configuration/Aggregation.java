package com.ingby.socbox.bischeck.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ingby.socbox.bischeck.Util;
import com.ingby.socbox.bischeck.service.Service;
import com.ingby.socbox.bischeck.service.ServiceFactory;
import com.ingby.socbox.bischeck.serviceitem.ServiceItem;
import com.ingby.socbox.bischeck.serviceitem.ServiceItemFactory;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLAggregate;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLCache;
import com.ingby.socbox.bischeck.xsd.bischeck.XMLRetention;

/**
 * The class is used to create configuration for a service definition that is
 * configured with the aggregate tag.
 */
public class Aggregation {

	private static final String WEEKEND = "/weekend";

	enum AGGREGATION  { 
		HOUR { 
			public String toString() {
				return "HOUR";
			}
			public String prefix() {
				return "H";
			}
			public String execStatInclWeekend() {
				return "[-0H:-1H]";
			}
			public String execStat() {
				return "[-0H:-1H]";
			}
			public String execPrefix() {
				return "";
			}
			public String scheduleInclWeekend() {
				return "0 0 * ? * *";
			}
			public String schedule() {
				return "0 0 * ? * MON-FRI";
			}
		}, 
		DAY { 
			public String toString() {
				return "DAY";
			}
			public String prefix() {
				return "D";
			}
			public String execStatInclWeekend() {
				return "[0:24]";
			}
			public String execStat() {
				return "[0:24]";
			}
			public String execPrefix() {
				return "/H/";
			}
			public String scheduleInclWeekend() {
				return "0 59 23 ? * *";
			}
			public String schedule() {
				return "0 59 23 ? * MON-FRI";
			}
		}, 
		WEEK { 
			public String toString() {
				return "WEEK";
			}
			public String prefix() {
				return "W";
			}
			public String execStatInclWeekend() {
				return "[0:7]";
			}
			public String execStat() {
				return "[0:5]";
			}
			public String execPrefix() {
				return "/D/";
			}
			public String scheduleInclWeekend() {
				return "0 59 23 ? * SUN";
			}
			public String schedule() {
				return "0 59 23 ? * FRI";
			}
		}, 

		MONTH {         	 
			public String toString() {
				return "MONTH";
			}
			public String prefix() {
				return "M";
			}
			public String execStatInclWeekend() {
				return "[0:4]";
			}
			public String execStat() {
				return "[0:4]";
			}
			public String execPrefix() {
				return "/W/";
			}
			public String scheduleInclWeekend() {
				return "0 59 23 L * ?";
			}
			public String schedule() {
				return "0 59 23 L * ?";
			}
		};

		public abstract String prefix();
		public abstract String execStat();
		public abstract String execStatInclWeekend();
		public abstract String execPrefix();
		public abstract String schedule();
		public abstract String scheduleInclWeekend();

	}


	private XMLCache xmlconfig;
	private Service baseService;
	private ServiceItem baseServiceitem;
	private Map<String,String> retentionMap = new HashMap<String,String>();

	public Aggregation(XMLCache xmlconfig, Service service, ServiceItem serviceitem) {
		this.xmlconfig = xmlconfig;
		this.baseService = service;
		this.baseServiceitem = serviceitem;
	}

	void setAggregate() throws Exception {
		if (xmlconfig == null)
			return;

		ArrayList<AGGREGATION> periods = new ArrayList<AGGREGATION>();
		periods.add(AGGREGATION.HOUR);
		periods.add(AGGREGATION.DAY);
		periods.add(AGGREGATION.WEEK);
		periods.add(AGGREGATION.MONTH);

		for (AGGREGATION period:periods) {
			for (XMLAggregate aggregated: xmlconfig.getAggregate()) {

				Service service = null;

				if (aggregated.isUseweekend()) {
					service = ServiceFactory.createService(
							baseService.getServiceName()+ "/" + period.prefix() + "/" + aggregated.getMethod() + WEEKEND,
							"bischeck://cache");
				} else {
					service = ServiceFactory.createService(
							baseService.getServiceName()+ "/" + period.prefix()  + "/" + aggregated.getMethod(),
							"bischeck://cache");
				}


				service.setHost(baseService.getHost());
				service.setDecscription("");
				service.setSchedules(getAggregatedSchedule(period,aggregated.isUseweekend()));
				service.setConnectionUrl("bischeck://cache");
				service.setSendServiceData(false);

				ServiceItem serviceItem = null;

				serviceItem = ServiceItemFactory.createServiceItem(
						baseServiceitem.getServiceItemName(),
						"CalculateOnCache");

				serviceItem.setClassName("CalculateonCache");
				serviceItem.setExecution(getAggregatedExecution(period, aggregated,baseService,baseServiceitem));


				serviceItem.setService(service);
				service.addServiceItem(serviceItem);
				baseService.getHost().addService(service);

				setRetention(period, aggregated, service,
						serviceItem);
			}
		}
	}

	private void setRetention(AGGREGATION period, XMLAggregate aggregated,
			Service service, ServiceItem serviceItem) {
		// Calculate the retention if it exists
		for (XMLRetention retention: aggregated.getRetention()){
			if (retention.getPeriod().equals(period.prefix())) {
				retentionMap .put(Util.fullName(service, serviceItem),String.valueOf(retention.getOffset()));
			}
		}
	}

	
	Map<String,String> getRetentionMap() {
		return retentionMap;
	}

	
	private String getAggregatedExecution(AGGREGATION agg, XMLAggregate aggregated,
			Service service, ServiceItem serviceitem) {
		String execStatement = null;	

		if (agg.toString().equals("HOUR")) {
			execStatement = aggregated.getMethod() + "(" + Util.fullQoutedName(service, serviceitem) + 
					agg.execStatInclWeekend() +")";
		} else {

			if (aggregated.isUseweekend()) {
				execStatement = aggregated.getMethod() + "(" + Util.fullQoutedName(service, serviceitem, 
						agg.execPrefix()+ aggregated.getMethod() + WEEKEND) +
						agg.execStatInclWeekend() + 
						")";

			} else {
				execStatement = aggregated.getMethod() + "(" + Util.fullQoutedName(service, serviceitem, 
						agg.execPrefix()+ aggregated.getMethod()) + 
						agg.execStat()+ ")";
			}	
		}
		return execStatement;
	}


	private List<String> getAggregatedSchedule(AGGREGATION agg, Boolean useWeekend) {
		List<String> schedules = new ArrayList<String>();

		if (useWeekend) {
			schedules.add(agg.scheduleInclWeekend());
		} else {
			schedules.add(agg.schedule());
		}

		return schedules;
	}

}
