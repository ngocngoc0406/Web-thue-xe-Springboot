package com.project.CarRental2.api.dto;

public class ChatMessageRequest {
    private String message;
    private Integer topK = 5;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
