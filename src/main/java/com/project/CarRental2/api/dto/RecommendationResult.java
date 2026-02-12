package com.project.CarRental2.api.dto;

public class RecommendationResult {
    private int idCar;
    private String nameCar;
    private String avatarCar;
    private int price;
    private int numberOfSeats;
    private double score;
    private String reason;

    public RecommendationResult() {
    }

    public RecommendationResult(int idCar, String nameCar, String avatarCar, int price, int numberOfSeats, double score,
            String reason) {
        this.idCar = idCar;
        this.nameCar = nameCar;
        this.avatarCar = avatarCar;
        this.price = price;
        this.numberOfSeats = numberOfSeats;
        this.score = score;
        this.reason = reason;
    }

    // getters and setters
    public int getIdCar() {
        return idCar;
    }

    public void setIdCar(int idCar) {
        this.idCar = idCar;
    }

    public String getNameCar() {
        return nameCar;
    }

    public void setNameCar(String nameCar) {
        this.nameCar = nameCar;
    }

    public String getAvatarCar() {
        return avatarCar;
    }

    public void setAvatarCar(String avatarCar) {
        this.avatarCar = avatarCar;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getNumberOfSeats() {
        return numberOfSeats;
    }

    public void setNumberOfSeats(int numberOfSeats) {
        this.numberOfSeats = numberOfSeats;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
