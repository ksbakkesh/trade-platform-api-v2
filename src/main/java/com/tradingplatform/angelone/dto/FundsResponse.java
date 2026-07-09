package com.tradingplatform.angelone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FundsResponse {

    @JsonProperty("net")
    private String net;

    @JsonProperty("availablecash")
    private String availableCash;

    @JsonProperty("availableintradaypayin")
    private String availableIntradayPayin;

    @JsonProperty("availablelimitmargin")
    private String availableLimitMargin;

    @JsonProperty("collateral")
    private String collateral;

    @JsonProperty("m2munrealized")
    private String m2mUnrealized;

    @JsonProperty("m2mrealized")
    private String m2mRealized;

    @JsonProperty("utiliseddebits")
    private String utilisedDebits;

    @JsonProperty("utilisedpayout")
    private String utilisedPayout;

    public String getNet() { return net; }
    public void setNet(String v) { this.net = v; }

    public String getAvailableCash() { return availableCash; }
    public void setAvailableCash(String v) { this.availableCash = v; }

    public String getAvailableIntradayPayin() { return availableIntradayPayin; }
    public void setAvailableIntradayPayin(String v) { this.availableIntradayPayin = v; }

    public String getAvailableLimitMargin() { return availableLimitMargin; }
    public void setAvailableLimitMargin(String v) { this.availableLimitMargin = v; }

    public String getCollateral() { return collateral; }
    public void setCollateral(String v) { this.collateral = v; }

    public String getM2mUnrealized() { return m2mUnrealized; }
    public void setM2mUnrealized(String v) { this.m2mUnrealized = v; }

    public String getM2mRealized() { return m2mRealized; }
    public void setM2mRealized(String v) { this.m2mRealized = v; }

    public String getUtilisedDebits() { return utilisedDebits; }
    public void setUtilisedDebits(String v) { this.utilisedDebits = v; }

    public String getUtilisedPayout() { return utilisedPayout; }
    public void setUtilisedPayout(String v) { this.utilisedPayout = v; }
}
