package com.project.CarRental2.api;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.CarRental2.model.Car;
import com.project.CarRental2.repository.CarRepository;
import com.project.CarRental2.service.AiChatService;

/**
 * AI Review Summary Controller
 * Generates AI-powered summaries of car reviews, highlighting pros, cons, and
 * overall rating
 */
@RestController
@RequestMapping("/api/ai")
public class AiReviewSummaryController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private CarRepository carRepository;

    // Cache AI results with size limit: carId -> {result, timestamp}
    private static final int MAX_CACHE_SIZE = 200;
    private static final ConcurrentHashMap<Integer, CachedResult> aiCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 30 * 60 * 1000; // 30 minutes

    private static class CachedResult {
        Map<String, Object> data;
        long timestamp;
        CachedResult(Map<String, Object> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
    }

    /**
     * Instant endpoint - returns local-generated results immediately (no AI API call)
     */
    @GetMapping("/review-summary-instant/{carId}")
    public ResponseEntity<?> getInstantReviewSummary(@PathVariable("carId") int carId) {
        try {
            // Check cache first
            CachedResult cached = aiCache.get(carId);
            if (cached != null && !cached.isExpired()) {
                Map<String, Object> result = new HashMap<>(cached.data);
                result.put("source", "cached");
                return ResponseEntity.ok(result);
            }

            Car car = carRepository.findById(carId).orElse(null);
            if (car == null) {
                return ResponseEntity.notFound().build();
            }

            // Generate local result instantly (no API call)
            Map<String, Object> result = generateLocalResult(car);
            result.put("source", "local");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Lỗi: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Full AI endpoint - calls AI API (with cache)
     */
    @GetMapping("/review-summary/{carId}")
    public ResponseEntity<?> getReviewSummary(@PathVariable("carId") int carId) {
        try {
            // Check cache first
            CachedResult cached = aiCache.get(carId);
            if (cached != null && !cached.isExpired()) {
                Map<String, Object> result = new HashMap<>(cached.data);
                result.put("source", "cached");
                return ResponseEntity.ok(result);
            }

            Car car = carRepository.findById(carId).orElse(null);
            if (car == null) {
                return ResponseEntity.notFound().build();
            }

            // Build prompt for AI to analyze the car
            String prompt = buildAnalysisPrompt(car);

            // Get AI analysis
            String aiResponse = aiChatService.getReply(prompt, "REVIEW_SUMMARY_" + carId, new HashMap<>());

            // Parse response into structured format
            Map<String, Object> result = parseAiResponse(aiResponse, car);
            result.put("source", "ai");

            // Cache the result (with size limit)
            evictOldestIfNeeded();
            aiCache.put(carId, new CachedResult(result));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // On AI failure, return local result instead of error
            try {
                Car car = carRepository.findById(carId).orElse(null);
                if (car != null) {
                    Map<String, Object> result = generateLocalResult(car);
                    result.put("source", "local-fallback");
                    return ResponseEntity.ok(result);
                }
            } catch (Exception ignored) {}
            Map<String, String> error = new HashMap<>();
            error.put("error", "Không thể tạo phân tích AI: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Clear all cached AI review results (admin use)
     */
    @DeleteMapping("/review-summary/clear-cache")
    public ResponseEntity<?> clearCache() {
        int size = aiCache.size();
        aiCache.clear();
        Map<String, Object> result = new HashMap<>();
        result.put("cleared", size);
        result.put("message", "Đã xoá " + size + " entries từ cache");
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> generateLocalResult(Car car) {
        Map<String, Object> result = new HashMap<>();
        result.put("carName", car.getNameCar());
        result.put("carId", car.getIdCar());
        result.put("overview", generateUniqueOverview(car));
        result.put("strengths", generateUniqueStrengths(car));
        result.put("notes", generateUniqueNotes(car));
        result.put("suitableFor", generateUniqueSuitableFor(car));
        result.put("rating", calculateRating(car, ""));
        return result;
    }

    /**
     * Evict oldest cache entries when cache exceeds MAX_CACHE_SIZE
     */
    private void evictOldestIfNeeded() {
        if (aiCache.size() >= MAX_CACHE_SIZE) {
            // Find and remove the oldest entry
            Integer oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<Integer, CachedResult> entry : aiCache.entrySet()) {
                if (entry.getValue().timestamp < oldestTime) {
                    oldestTime = entry.getValue().timestamp;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) {
                aiCache.remove(oldestKey);
            }
        }
    }

    private String buildAnalysisPrompt(Car car) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hãy phân tích và đưa ra đánh giá chi tiết về xe sau đây:\n\n");
        sb.append("Tên xe: ").append(car.getNameCar()).append("\n");
        sb.append("Số ghế: ").append(car.getNumberOfSeats()).append("\n");
        sb.append("Giá thuê: ").append(car.getPrice()).append(" VND/ngày\n");
        sb.append("Nhiên liệu tiêu thụ: ").append(car.getFuelFor100km()).append(" L/100km\n");
        sb.append("Mô tả: ").append(car.getOverviewCar() != null ? car.getOverviewCar() : "Không có").append("\n\n");

        // Features list
        sb.append("Tính năng: ");
        if (car.isMaps())
            sb.append("GPS/Bản đồ, ");
        if (car.isBluetooth())
            sb.append("Bluetooth, ");
        if (car.isCamera360())
            sb.append("Camera 360, ");
        if (car.isParkingCamera())
            sb.append("Camera cặp lề, ");
        if (car.isDashCamera())
            sb.append("Camera hành trình, ");
        if (car.isReverseCamera())
            sb.append("Camera lùi, ");
        if (car.isTpms())
            sb.append("Cảm biến áp suất lốp, ");
        if (car.isImpactSensor())
            sb.append("Cảm biến va chạm, ");
        if (car.isSpeedWarning())
            sb.append("Cảnh báo tốc độ, ");
        sb.append("\n\n");

        sb.append("Hãy trả lời theo đúng format sau (QUAN TRỌNG - giữ nguyên các nhãn):\n");
        sb.append("TỔNG QUAN: [Viết 2-3 câu tổng quan về xe]\n");
        sb.append("ĐIỂM MẠNH:\n- [Điểm mạnh 1]\n- [Điểm mạnh 2]\n- [Điểm mạnh 3]\n");
        sb.append("CẦN LƯU Ý:\n- [Điểm cần lưu ý 1]\n- [Điểm cần lưu ý 2]\n");
        sb.append("PHÙ HỢP VỚI: [Mô tả đối tượng phù hợp với xe này]\n");
        sb.append("ĐIỂM ĐÁNH GIÁ: [Cho điểm từ 1-5, ví dụ: 4.5]\n");

        return sb.toString();
    }

    private Map<String, Object> parseAiResponse(String response, Car car) {
        Map<String, Object> result = new HashMap<>();
        result.put("carName", car.getNameCar());
        result.put("carId", car.getIdCar());

        // Extract sections from response
        String overview = extractSection(response, "TỔNG QUAN:", new String[] { "ĐIỂM MẠNH:", "CẦN LƯU Ý:" });
        String strengths = extractSection(response, "ĐIỂM MẠNH:", new String[] { "CẦN LƯU Ý:", "PHÙ HỢP VỚI:" });
        String notes = extractSection(response, "CẦN LƯU Ý:", new String[] { "PHÙ HỢP VỚI:", "ĐIỂM ĐÁNH GIÁ:" });
        String suitableFor = extractSection(response, "PHÙ HỢP VỚI:", new String[] { "ĐIỂM ĐÁNH GIÁ:" });
        String rating = extractSection(response, "ĐIỂM ĐÁNH GIÁ:", new String[] {});

        // Generate unique default values if parsing fails - based on actual car info
        if (overview.isEmpty()) {
            overview = generateUniqueOverview(car);
        }
        if (strengths.isEmpty()) {
            strengths = generateUniqueStrengths(car);
        }
        if (notes.isEmpty()) {
            notes = generateUniqueNotes(car);
        }
        if (suitableFor.isEmpty()) {
            suitableFor = generateUniqueSuitableFor(car);
        }

        // Parse or calculate rating based on features
        double ratingValue = calculateRating(car, rating);

        result.put("overview", overview.trim());
        result.put("strengths", strengths.trim());
        result.put("notes", notes.trim());
        result.put("suitableFor", suitableFor.trim());
        result.put("rating", ratingValue);
        result.put("rawResponse", response);

        return result;
    }

    private String generateUniqueOverview(Car car) {
        StringBuilder sb = new StringBuilder();
        sb.append(car.getNameCar()).append(" là mẫu xe ").append(car.getNumberOfSeats()).append(" chỗ");

        if (car.getPromotionalPrice() > 0) {
            sb.append(" đang được giảm giá ").append(car.getPromotionalPrice()).append("%");
        }

        sb.append(". Với giá thuê ").append(formatPrice(car.getPrice())).append("đ/ngày");

        int featureCount = countFeatures(car);
        if (featureCount >= 5) {
            sb.append(", xe được trang bị đầy đủ tiện nghi hiện đại");
        } else if (featureCount >= 3) {
            sb.append(", xe có các tính năng cơ bản cần thiết");
        }
        sb.append(".");

        return sb.toString();
    }

    private String generateUniqueStrengths(Car car) {
        StringBuilder sb = new StringBuilder();

        // Based on actual features
        if (car.isCamera360())
            sb.append("- Camera 360 độ quan sát toàn cảnh\n");
        if (car.isBluetooth())
            sb.append("- Kết nối Bluetooth tiện lợi\n");
        if (car.isMaps() || car.isGpsLocator())
            sb.append("- GPS/Bản đồ định vị\n");
        if (car.isReverseCamera())
            sb.append("- Camera lùi hỗ trợ đỗ xe\n");
        if (car.isDashCamera())
            sb.append("- Camera hành trình ghi hình\n");
        if (car.isSunroof())
            sb.append("- Cửa sổ trời cao cấp\n");
        if (car.isUsb())
            sb.append("- Cổng USB sạc thiết bị\n");
        if (car.isAirbags())
            sb.append("- Túi khí an toàn\n");

        // Price advantage
        if (car.getPromotionalPrice() > 0) {
            sb.append("- Đang có ưu đãi giảm giá ").append(car.getPromotionalPrice()).append("%\n");
        }

        // Seats advantage
        if (car.getNumberOfSeats() >= 7) {
            sb.append("- Rộng rãi với ").append(car.getNumberOfSeats()).append(" chỗ ngồi\n");
        }

        // Default if no features
        if (sb.length() == 0) {
            sb.append("- ").append(car.getNumberOfSeats()).append(" chỗ ngồi thoải mái\n");
            sb.append("- Giá thuê hợp lý\n");
        }

        return sb.toString().trim();
    }

    private String generateUniqueNotes(Car car) {
        StringBuilder sb = new StringBuilder();

        // Transmission type
        if (car.isManualTransmissionCar()) {
            sb.append("- Xe số sàn, cần có kinh nghiệm lái\n");
        }

        // Fuel consumption
        if (car.getFuelFor100km() > 10) {
            sb.append("- Tiêu thụ nhiên liệu hơi cao (").append(car.getFuelFor100km()).append("L/100km)\n");
        }

        // Big car note
        if (car.getNumberOfSeats() >= 7) {
            sb.append("- Xe lớn, cần chú ý khi đậu xe trong không gian hẹp\n");
        }

        // Default notes
        sb.append("- Kiểm tra xe kỹ trước khi nhận\n");

        return sb.toString().trim();
    }

    private String generateUniqueSuitableFor(Car car) {
        if (car.getNumberOfSeats() <= 4) {
            return "Cặp đôi, cá nhân, công việc hàng ngày";
        } else if (car.getNumberOfSeats() <= 5) {
            return "Gia đình nhỏ, du lịch ngắn ngày";
        } else if (car.getNumberOfSeats() <= 7) {
            return "Gia đình đông người, du lịch dài ngày";
        } else {
            return "Đoàn du lịch, công ty, nhóm bạn đông";
        }
    }

    private double calculateRating(Car car, String ratingText) {
        // Try to parse from AI response first
        try {
            String ratingNum = ratingText.replaceAll("[^0-9.]", "");
            if (!ratingNum.isEmpty()) {
                double parsed = Double.parseDouble(ratingNum);
                if (parsed >= 1 && parsed <= 5) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            // Fall through to calculation
        }

        // Calculate rating based on car features
        double rating = 3.5; // Base rating

        int featureCount = countFeatures(car);
        rating += featureCount * 0.1; // +0.1 per feature

        // Promotional bonus
        if (car.getPromotionalPrice() > 0) {
            rating += 0.2;
        }

        // Good fuel economy bonus
        if (car.getFuelFor100km() > 0 && car.getFuelFor100km() <= 8) {
            rating += 0.2;
        }

        // Cap at 5
        return Math.min(5.0, Math.round(rating * 10) / 10.0);
    }

    private int countFeatures(Car car) {
        int count = 0;
        if (car.isMaps())
            count++;
        if (car.isBluetooth())
            count++;
        if (car.isCamera360())
            count++;
        if (car.isReverseCamera())
            count++;
        if (car.isDashCamera())
            count++;
        if (car.isSunroof())
            count++;
        if (car.isUsb())
            count++;
        if (car.isAirbags())
            count++;
        if (car.isGpsLocator())
            count++;
        if (car.isParkingCamera())
            count++;
        if (car.isTpms())
            count++;
        if (car.isSpeedWarning())
            count++;
        return count;
    }

    private String formatPrice(int price) {
        if (price >= 1000000) {
            return String.format("%,.0fK", price / 1000.0).replace(',', '.');
        } else if (price >= 1000) {
            return String.format("%dK", price / 1000);
        }
        return String.format("%,d", price).replace(',', '.');
    }

    private String extractSection(String text, String startLabel, String[] endLabels) {
        int startIndex = text.indexOf(startLabel);
        if (startIndex == -1)
            return "";

        startIndex += startLabel.length();

        int endIndex = text.length();
        for (String endLabel : endLabels) {
            int idx = text.indexOf(endLabel, startIndex);
            if (idx != -1 && idx < endIndex) {
                endIndex = idx;
            }
        }

        return text.substring(startIndex, endIndex).trim();
    }
}
