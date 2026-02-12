package com.project.CarRental2.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.CarRental2.service.CarRecommendationService;
import com.project.CarRental2.service.CarRecommendationService.CarRecommendation;

/**
 * API Controller cho hệ thống gợi ý xe AI
 * Sử dụng Collaborative Filtering + Content-Based Filtering
 */
@RestController
@RequestMapping("/api/recommendation")
public class CarRecommendationController {

    @Autowired
    private CarRecommendationService recommendationService;

    /**
     * Lấy danh sách xe gợi ý cho user
     * GET /api/recommendation/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getRecommendations(@PathVariable("userId") int userId) {
        try {
            List<CarRecommendation> recommendations = recommendationService.getRecommendations(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("recommendations", recommendations);
            response.put("count", recommendations.size());
            response.put("algorithm", "Collaborative Filtering + Content-Based");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể lấy gợi ý: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Ghi nhận user xem xe (tracking)
     * POST /api/recommendation/track-view
     * Body: { "userId": 1, "carId": 5 }
     */
    @PostMapping("/track-view")
    public ResponseEntity<?> trackView(@RequestBody TrackViewRequest request) {
        try {
            if (request.getUserId() <= 0 || request.getCarId() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "userId và carId phải > 0"));
            }

            recommendationService.trackCarView(request.getUserId(), request.getCarId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Đã ghi nhận lịch sử xem xe"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Lỗi ghi nhận: " + e.getMessage()));
        }
    }

    /**
     * DTO cho request track view
     */
    public static class TrackViewRequest {
        private int userId;
        private int carId;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public int getCarId() {
            return carId;
        }

        public void setCarId(int carId) {
            this.carId = carId;
        }
    }
}
