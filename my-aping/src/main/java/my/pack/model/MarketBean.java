package my.pack.model;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Meta information about market.
 * 
 * @author VLD
 * 
 */
@JsonInclude(Include.NON_NULL)
public class MarketBean {

	@JsonProperty("mrktStart")
	private Long marketStartTime;
	@JsonProperty("monitorStart")
	private Long startMonitoringTime;
	@JsonProperty("monitorEnd")
	private Long endMonitoringTime;
	@JsonProperty("hid")
	private ArrayList<Long> horsesId;
	@JsonProperty("cnt")
	private Integer cntOfProbes;
	/**
	 * OK - successfully validating
	 * WARN - validating with warnings, e.g. big delta
	 * FAIL - some fields doesn't exist 
	 */
	@JsonProperty("val")
	private String statusOfValidate;
	@JsonProperty("proc")
	private Boolean processed;
	@JsonProperty("missedTime")
	private Long missedTime;

	public MarketBean() {

	}

	public MarketBean(Long marketStartTime, Long startMonitoringTime,
			Long endMonitoringTime, ArrayList<Long> horsesId,
			Integer cntOfProbes, String validated, Boolean processed, Long missedTime) {
		super();
		this.marketStartTime = marketStartTime;
		this.startMonitoringTime = startMonitoringTime;
		this.endMonitoringTime = endMonitoringTime;
		this.horsesId = horsesId;
		this.cntOfProbes = cntOfProbes;
		this.setValidated(validated);
		this.setProcessed(processed);
		this.setMissedTime(missedTime);
	}

	public Long getMarketStartTime() {
		return marketStartTime;
	}

	public void setMarketStartTime(Long marketStartTime) {
		this.marketStartTime = marketStartTime;
	}

	public Long getStartMonitoringTime() {
		return startMonitoringTime;
	}

	public void setStartMonitoringTime(Long startMonitoringTime) {
		this.startMonitoringTime = startMonitoringTime;
	}

	public Long getEndMonitoringTime() {
		return endMonitoringTime;
	}

	public void setEndMonitoringTime(Long endMonitoringTime) {
		this.endMonitoringTime = endMonitoringTime;
	}

	public ArrayList<Long> getHorsesId() {
		return horsesId;
	}

	public void setHorsesId(ArrayList<Long> horsesId) {
		this.horsesId = horsesId;
	}

	public Integer getCntOfProbes() {
		return cntOfProbes;
	}

	public void setCntOfProbes(Integer cntOfProbes) {
		this.cntOfProbes = cntOfProbes;
	}

	public String getValidated() {
		return statusOfValidate;
	}

	public void setValidated(String validated) {
		this.statusOfValidate = validated;
	}

	public Boolean getProcessed() {
		return processed;
	}

	public void setProcessed(Boolean processed) {
		this.processed = processed;
	}

	public Long getMissedTime() {
		return missedTime;
	}

	public void setMissedTime(Long missedTime) {
		this.missedTime = missedTime;
	}

}
