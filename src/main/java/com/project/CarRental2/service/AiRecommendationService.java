package com.project.CarRental2.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.CarRental2.api.dto.RecommendationRequest;
import com.project.CarRental2.api.dto.RecommendationResult;
import com.project.CarRental2.model.Car;
import com.project.CarRental2.repository.CarRepository;

@Service
public class AiRecommendationService {

    @Autowired
    private CarRepository carRepository;

    // PoC rule-based recommender
    public List<RecommendationResult> recommend(RecommendationRequest req) {
        List<Car> candidates = fetchCandidates(req);
        List<ScoredCar> scored = new ArrayList<>();

        for (Car c : candidates) {
            double score = 0.0;
            StringBuilder reason = new StringBuilder();

            // price scoring
            if (req.getMinPrice() != null || req.getMaxPrice() != null) {
                int min = req.getMinPrice() != null ? req.getMinPrice() : 0;
                int max = req.getMaxPrice() != null ? req.getMaxPrice() : Integer.MAX_VALUE;
                if (c.getPromotionalPrice() > 0) {
                    // use promotionalPrice if set
                    if (c.getPromotionalPrice() >= min && c.getPromotionalPrice() <= max) {
                        score += 2.0;
                        reason.append("price ok; ");
                    }
                } else if (c.getPrice() >= min && c.getPrice() <= max) {
                    score += 1.5;
                    reason.append("price ok; ");
                } else {
                    // penalize if out of range
                    score -= 0.5;
                }
            }

            // seats
            if (req.getMinSeats() != null) {
                if (c.getNumberOfSeats() >= req.getMinSeats()) {
                    score += 1.0;
                    reason.append("seats ok; ");
                } else {
                    score -= 0.2;
                }
            }

            // required features
            if (req.getRequiredFeatures() != null && !req.getRequiredFeatures().isEmpty()) {
                int matched = 0;
                for (String f : req.getRequiredFeatures()) {
                    if (hasFeature(c, f.toLowerCase(Locale.ROOT))) {
                        matched++;
                    }
                }
                score += matched * 0.8;
                reason.append("features matched=" + matched + "; ");
            }

            // text match simple
            if (req.getText() != null && !req.getText().isBlank()) {
                String txt = req.getText().toLowerCase(Locale.ROOT);
                int hits = 0;
                if (c.getNameCar() != null && c.getNameCar().toLowerCase(Locale.ROOT).contains(txt))
                    hits++;
                if (c.getOverviewCar() != null && c.getOverviewCar().toLowerCase(Locale.ROOT).contains(txt))
                    hits++;
                score += hits * 0.7;
                if (hits > 0)
                    reason.append("text match=" + hits + "; ");
            }

            // normalize/bonus: prefer lower price when both in range
            if (req.getMinPrice() != null || req.getMaxPrice() != null) {
                int priceVal = c.getPromotionalPrice() > 0 ? c.getPromotionalPrice() : c.getPrice();
                double priceBonus = 1.0 / (1 + priceVal / 1000.0); // small bonus for cheaper
                score += priceBonus;
            }

            scored.add(new ScoredCar(c, score, reason.toString()));
        }

        List<RecommendationResult> results = scored.stream()
                .sorted(Comparator.comparingDouble(ScoredCar::getScore).reversed())
                .limit(req.getTopK() == null ? 10 : req.getTopK())
                .map(s -> toResult(s))
                .collect(Collectors.toList());

        return results;
    }

    private RecommendationResult toResult(ScoredCar s) {
        Car c = s.car;
        return new RecommendationResult(c.getIdCar(), c.getNameCar(), c.getAvatarCar(),
                c.getPromotionalPrice() > 0 ? c.getPromotionalPrice() : c.getPrice(), c.getNumberOfSeats(), s.score,
                s.reason);
    }

    private List<Car> fetchCandidates(RecommendationRequest req) {
        boolean driver = req.getDriver() != null ? req.getDriver() : true;
        String address = req.getAddress() != null ? req.getAddress() : "";
        // if date provided, use availability query
        if (req.getDateStart() != null && req.getDateEnd() != null && !req.getDateStart().isBlank()
                && !req.getDateEnd().isBlank()) {
            // status = 1 (assume active)
            try {
                return carRepository.findCarOnTimeByDriverAndAddress(driver, address, req.getDateStart(),
                        req.getDateEnd(), 1);
            } catch (Exception e) {
                // fallback to all
            }
        }

        if (!address.isBlank()) {
            return carRepository.getAllCarByDriverInAddressOderByName(driver, address);
        }

        return carRepository.getAllCarByDriverAndStatusCarOderByName(driver, 1);
    }

    private boolean hasFeature(Car c, String feature) {
        switch (feature) {
            case "gps":
                return c.isGpsLocator();
            case "babyseat":
                return c.isBabyseat();
            case "sunroof":
                return c.isSunroof();
            case "dvd":
                return c.isDvdScreen();
            case "bluetooth":
                return c.isBluetooth();
            case "reversecamera":
                return c.isReverseCamera();
            case "dashcamera":
                return c.isDashCamera();
            case "camera360":
                return c.isCamera360();
            case "airbags":
                return c.isAirbags();
            case "usb":
                return c.isUsb();
            case "driver":
                return c.isDriver();
            default:
                return false;
        }
    }

    private static class ScoredCar {
        private Car car;
        private double score;
        private String reason;

        ScoredCar(Car car, double score, String reason) {
            this.car = car;
            this.score = score;
            this.reason = reason;
        }

        public double getScore() {
            return score;
        }
    }
}
