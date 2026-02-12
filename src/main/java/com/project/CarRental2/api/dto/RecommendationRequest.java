package com.project.CarRental2.api.dto;

import java.util.List;

public class RecommendationRequest {
    private String text; // free-form user requirements
    private String address; // preferred address or location substring
    private Boolean driver; // require driver or not
    private String dateStart; // yyyy-MM-dd
    private String dateEnd; // yyyy-MM-dd
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minSeats;
    private List<String> requiredFeatures; // e.g. ["gps","babyseat","sunroof"]
    private Integer topK = 10;

    // getters and setters
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getDriver() {
        return driver;
    }

    public void setDriver(Boolean driver) {
        this.driver = driver;
    }

    public String getDateStart() {
        return dateStart;
    }

    public void setDateStart(String dateStart) {
        this.dateStart = dateStart;
    }

    public String getDateEnd() {
        return dateEnd;
    }

    public void setDateEnd(String dateEnd) {
        this.dateEnd = dateEnd;
    }

    public Integer getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(Integer minPrice) {
        this.minPrice = minPrice;
    }

    public Integer getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Integer maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getMinSeats() {
        return minSeats;
    }

    public void setMinSeats(Integer minSeats) {
        this.minSeats = minSeats;
    }

    public List<String> getRequiredFeatures() {
        return requiredFeatures;
    }

    public void setRequiredFeatures(List<String> requiredFeatures) {
        this.requiredFeatures = requiredFeatures;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
