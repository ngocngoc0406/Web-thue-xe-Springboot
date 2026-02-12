package com.project.CarRental2.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.CarRental2.model.CarReview;
import com.project.CarRental2.repository.CarReviewRepository;

@Service
public class CarReviewServiceImpl implements CarReviewService {

    @Autowired
    private CarReviewRepository carReviewRepository;

    @Override
    public CarReview saveReview(CarReview review) {
        CarReview saved = carReviewRepository.save(review);
        return saved != null ? saved : review;
    }

    @Override
    public List<CarReview> getReviewsByCarId(int idCar) {
        return carReviewRepository.findByCarIdCarOrderByCreateDateDesc(idCar);
    }

    @Override
    public double getAverageRating(int idCar) {
        List<CarReview> reviews = getReviewsByCarId(idCar);
        if (reviews.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (CarReview review : reviews) {
            sum += review.getRating();
        }
        return Math.round((sum / reviews.size()) * 10.0) / 10.0;
    }

    @Override
    public int getReviewCount(int idCar) {
        return getReviewsByCarId(idCar).size();
    }
}
