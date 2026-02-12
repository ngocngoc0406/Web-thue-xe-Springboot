package com.project.CarRental2.service;

import java.util.List;

import com.project.CarRental2.model.CarReview;

public interface CarReviewService {

    CarReview saveReview(CarReview review);

    List<CarReview> getReviewsByCarId(int idCar);

    double getAverageRating(int idCar);

    int getReviewCount(int idCar);
}
