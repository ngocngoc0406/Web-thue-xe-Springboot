package com.project.CarRental2.api.dto;

public class ChatMessage {
    private String sender;
    private String content;
    private String timestamp;
    private String type; // CHAT, JOIN, LEAVE
    private Boolean askAi; // if true, service should ask AI for a reply

    public ChatMessage() {
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getAskAi() {
        return askAi;
    }

    public void setAskAi(Boolean askAi) {
        this.askAi = askAi;
    }
}