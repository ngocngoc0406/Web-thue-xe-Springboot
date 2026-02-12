package com.project.CarRental2.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.CarRental2.api.dto.RecommendationRequest;
import com.project.CarRental2.api.dto.RecommendationResult;
import com.project.CarRental2.service.AiRecommendationService;

@RestController
@RequestMapping("/api/ai")
public class AiRecommendationController {

    @Autowired
    private AiRecommendationService aiService;

    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody RecommendationRequest request) {
        try {
            List<RecommendationResult> results = aiService.recommend(request);
            return new ResponseEntity<>(results, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error: " + e.getMessage());
        }
    }
}
