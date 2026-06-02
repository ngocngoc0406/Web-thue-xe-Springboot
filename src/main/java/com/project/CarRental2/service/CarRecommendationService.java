package com.project.CarRental2.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.CarRental2.model.Car;
import com.project.CarRental2.model.CarViewHistory;
import com.project.CarRental2.repository.BookingRepository;
import com.project.CarRental2.repository.CarRepository;
import com.project.CarRental2.repository.CarViewHistoryRepository;

/**
 * Service gợi ý xe sử dụng Collaborative Filtering + Content-Based Filtering
 * 
 * Thuật toán:
 * 1. Lấy lịch sử xem/đặt xe của user hiện tại
 * 2. Tìm các user tương tự (đã xem/đặt xe giống nhau)
 * 3. Lấy xe mà user tương tự thích nhưng user hiện tại chưa xem
 * 4. Kết hợp Content-Based: lọc theo đặc điểm xe
 * 5. Tính điểm và sắp xếp
 */
@Service
public class CarRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(CarRecommendationService.class);

    @Autowired
    private CarViewHistoryRepository viewHistoryRepo;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private static final int MAX_RECOMMENDATIONS = 6;
    private static final int MIN_SIMILAR_USERS = 2;

    // Cache: userId -> {recommendations, timestamp}
    private static final ConcurrentHashMap<Integer, CachedRecommendations> recCache = new ConcurrentHashMap<>();
    private static final long REC_CACHE_TTL = 10 * 60 * 1000; // 10 minutes

    private static class CachedRecommendations {
        List<CarRecommendation> data;
        long timestamp;
        CachedRecommendations(List<CarRecommendation> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > REC_CACHE_TTL;
        }
    }

    /**
     * Ghi nhận user xem xe (tracking)
     */
    public void trackCarView(int userId, int carId) {
        Optional<CarViewHistory> existing = viewHistoryRepo.findByIdUserAndIdCar(userId, carId);

        if (existing.isPresent()) {
            CarViewHistory history = existing.get();
            history.incrementViewCount();
            viewHistoryRepo.save(history);
        } else {
            CarViewHistory newHistory = new CarViewHistory(userId, carId);
            viewHistoryRepo.save(newHistory);
        }
    }

    /**
     * Lấy danh sách xe gợi ý cho user
     */
    public List<CarRecommendation> getRecommendations(int userId) {
        // Check cache first
        CachedRecommendations cached = recCache.get(userId);
        if (cached != null && !cached.isExpired()) {
            log.debug("[Recommendation] Cache hit for userId={}", userId);
            return cached.data;
        }

        // Bước 1: Lấy xe user đã xem
        List<Integer> viewedCarIds = viewHistoryRepo.findViewedCarIdsByUser(userId);

        // Nếu user chưa xem xe nào, trả về xe phổ biến
        if (viewedCarIds.isEmpty()) {
            return getPopularCars(MAX_RECOMMENDATIONS);
        }

        // Bước 2: Tìm user tương tự (Collaborative Filtering)
        List<Integer> similarUserIds = findSimilarUsers(userId, viewedCarIds);

        List<CarRecommendation> recommendations = new ArrayList<>();

        // Bước 3: Lấy xe từ user tương tự
        if (!similarUserIds.isEmpty()) {
            recommendations.addAll(getCollaborativeRecommendations(similarUserIds, viewedCarIds));
        }

        // Bước 4: Bổ sung bằng Content-Based nếu chưa đủ
        if (recommendations.size() < MAX_RECOMMENDATIONS) {
            List<CarRecommendation> contentBased = getContentBasedRecommendations(userId, viewedCarIds);
            for (CarRecommendation rec : contentBased) {
                if (recommendations.size() >= MAX_RECOMMENDATIONS)
                    break;
                if (recommendations.stream().noneMatch(r -> r.getCarId() == rec.getCarId())) {
                    recommendations.add(rec);
                }
            }
        }

        // Bước 5: Bổ sung xe phổ biến nếu vẫn chưa đủ
        if (recommendations.size() < MAX_RECOMMENDATIONS) {
            List<CarRecommendation> popular = getPopularCars(MAX_RECOMMENDATIONS - recommendations.size());
            for (CarRecommendation rec : popular) {
                if (recommendations.size() >= MAX_RECOMMENDATIONS)
                    break;
                if (!viewedCarIds.contains(rec.getCarId()) &&
                        recommendations.stream().noneMatch(r -> r.getCarId() == rec.getCarId())) {
                    recommendations.add(rec);
                }
            }
        }

        // Bước 6: Nếu vẫn chưa đủ 3 xe (ví dụ trong DB nhỏ nơi user đã xem hết các xe), cho phép nhận lại các xe đã xem làm gợi ý (sẽ được lọc bỏ xe hiện tại ở frontend)
        if (recommendations.size() < 3) {
            List<CarRecommendation> popular = getPopularCars(MAX_RECOMMENDATIONS);
            for (CarRecommendation rec : popular) {
                if (recommendations.size() >= MAX_RECOMMENDATIONS)
                    break;
                if (recommendations.stream().noneMatch(r -> r.getCarId() == rec.getCarId())) {
                    recommendations.add(rec);
                }
            }
        }

        // Cache kết quả
        recCache.put(userId, new CachedRecommendations(recommendations));
        log.info("[Recommendation] Generated {} recommendations for userId={}", recommendations.size(), userId);

        return recommendations;
    }

    /**
     * Tìm user tương tự dựa trên lịch sử xem xe
     * Sử dụng Jaccard Similarity
     */
    private List<Integer> findSimilarUsers(int userId, List<Integer> userViewedCars) {
        Set<Integer> userCarSet = new HashSet<>(userViewedCars);
        Map<Integer, Double> userSimilarity = new HashMap<>();

        // Với mỗi xe user đã xem, tìm các user khác đã xem xe đó
        for (int carId : userViewedCars) {
            List<Integer> otherUsers = viewHistoryRepo.findUsersByCarId(carId, userId);
            for (int otherUserId : otherUsers) {
                // Tính Jaccard Similarity
                List<Integer> otherUserCars = viewHistoryRepo.findViewedCarIdsByUser(otherUserId);
                double similarity = calculateJaccardSimilarity(userCarSet, new HashSet<>(otherUserCars));

                userSimilarity.merge(otherUserId, similarity, Double::max);
            }
        }

        // Sắp xếp và lấy top similar users
        return userSimilarity.entrySet().stream()
                .filter(e -> e.getValue() > 0.1) // Chỉ lấy user có similarity > 10%
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Tính Jaccard Similarity giữa 2 tập hợp
     * Jaccard = |A ∩ B| / |A ∪ B|
     */
    private double calculateJaccardSimilarity(Set<Integer> set1, Set<Integer> set2) {
        if (set1.isEmpty() && set2.isEmpty())
            return 0;

        Set<Integer> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Integer> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Lấy xe gợi ý từ Collaborative Filtering
     */
    private List<CarRecommendation> getCollaborativeRecommendations(
            List<Integer> similarUserIds, List<Integer> excludeCarIds) {

        List<CarRecommendation> recommendations = new ArrayList<>();

        try {
            List<Object[]> results = viewHistoryRepo.findRecommendedCars(similarUserIds, excludeCarIds);

            for (Object[] row : results) {
                if (recommendations.size() >= MAX_RECOMMENDATIONS)
                    break;

                int carId = ((Number) row[0]).intValue();
                long totalViews = ((Number) row[1]).longValue();

                Optional<Car> carOpt = carRepository.findById(carId);
                if (carOpt.isPresent() && carOpt.get().getStatus() == 1) {
                    Car car = carOpt.get();
                    double score = Math.min(1.0, totalViews / 10.0); // Normalize score
                    recommendations.add(new CarRecommendation(car, score, "collaborative"));
                }
            }
        } catch (Exception e) {
            log.error("[Recommendation] Collaborative error: {}", e.getMessage());
        }

        return recommendations;
    }

    /**
     * Content-Based Filtering: Gợi ý xe tương tự dựa trên đặc điểm
     */
    private List<CarRecommendation> getContentBasedRecommendations(int userId, List<Integer> viewedCarIds) {
        List<CarRecommendation> recommendations = new ArrayList<>();

        if (viewedCarIds.isEmpty())
            return recommendations;

        // Lấy đặc điểm xe user đã xem
        List<Car> viewedCars = new ArrayList<>();
        for (int carId : viewedCarIds) {
            carRepository.findById(carId).ifPresent(viewedCars::add);
        }

        if (viewedCars.isEmpty())
            return recommendations;

        // Tính profile: số ghế phổ biến, khoảng giá trung bình
        int avgSeats = (int) viewedCars.stream().mapToInt(Car::getNumberOfSeats).average().orElse(4);
        int avgPrice = (int) viewedCars.stream().mapToInt(Car::getPrice).average().orElse(500000);

        // Đếm features phổ biến
        int bluetoothCount = (int) viewedCars.stream().filter(Car::isBluetooth).count();
        int cameraCount = (int) viewedCars.stream().filter(Car::isCamera360).count();

        // Lấy tất cả xe chưa xem
        List<Car> allCars = carRepository.getAllCarOrderByNameCarAsc();
        Set<Integer> viewedSet = new HashSet<>(viewedCarIds);

        for (Car car : allCars) {
            if (viewedSet.contains(car.getIdCar()) || car.getStatus() != 1)
                continue;

            // Tính similarity score
            double score = calculateContentSimilarity(car, avgSeats, avgPrice, bluetoothCount > 0, cameraCount > 0);

            if (score > 0.3) {
                recommendations.add(new CarRecommendation(car, score, "content-based"));
            }
        }

        // Sắp xếp theo score
        recommendations.sort(Comparator.comparingDouble(CarRecommendation::getScore).reversed());

        return recommendations.stream().limit(MAX_RECOMMENDATIONS).collect(Collectors.toList());
    }

    /**
     * Tính Content Similarity
     */
    private double calculateContentSimilarity(Car car, int targetSeats, int targetPrice,
            boolean preferBluetooth, boolean preferCamera) {
        double score = 0;

        // Số ghế giống: +0.3
        if (car.getNumberOfSeats() == targetSeats) {
            score += 0.3;
        } else if (Math.abs(car.getNumberOfSeats() - targetSeats) <= 2) {
            score += 0.15;
        }

        // Giá trong khoảng ±30%: +0.3
        double priceDiff = Math.abs(car.getPrice() - targetPrice) / (double) targetPrice;
        if (priceDiff <= 0.3) {
            score += 0.3 * (1 - priceDiff);
        }

        // Features matching: +0.2 each
        if (preferBluetooth && car.isBluetooth())
            score += 0.2;
        if (preferCamera && car.isCamera360())
            score += 0.2;

        return Math.min(1.0, score);
    }

    /**
     * Lấy xe phổ biến nhất (fallback)
     */
    private List<CarRecommendation> getPopularCars(int limit) {
        List<CarRecommendation> recommendations = new ArrayList<>();

        try {
            List<Object[]> popular = viewHistoryRepo.findMostPopularCars();

            for (Object[] row : popular) {
                if (recommendations.size() >= limit)
                    break;

                int carId = ((Number) row[0]).intValue();
                Optional<Car> carOpt = carRepository.findById(carId);

                if (carOpt.isPresent() && carOpt.get().getStatus() == 1) {
                    recommendations.add(new CarRecommendation(carOpt.get(), 0.5, "popular"));
                }
            }
        } catch (Exception e) {
            log.error("[Recommendation] Popular cars error: {}", e.getMessage());
        }

        // Bổ sung xe hoạt động từ hệ thống nếu danh sách gợi ý chưa đủ limit (hoặc rỗng khi mới cài đặt)
        if (recommendations.size() < limit) {
            try {
                List<Car> cars = carRepository.getAllCarOrderByNameCarAsc();
                for (Car car : cars) {
                    if (recommendations.size() >= limit)
                        break;
                    if (car.getStatus() == 1) {
                        // Tránh thêm trùng xe đã có
                        final int currentId = car.getIdCar();
                        if (recommendations.stream().noneMatch(r -> r.getCarId() == currentId)) {
                            recommendations.add(new CarRecommendation(car, 0.3, "default"));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Recommendation] Fallback error loading default cars: {}", e.getMessage());
            }
        }

        return recommendations;
    }

    /**
     * DTO cho kết quả gợi ý
     */
    public static class CarRecommendation {
        private int carId;
        private String carName;
        private String avatarCar;
        private int price;
        private int promotionalPrice;
        private int numberOfSeats;
        private double score;
        private String recommendationType;
        private String explanation;

        public CarRecommendation(Car car, double score, String type) {
            this.carId = car.getIdCar();
            this.carName = car.getNameCar();
            this.avatarCar = car.getAvatarCar();
            this.price = car.getPrice();
            this.promotionalPrice = car.getPromotionalPrice();
            this.numberOfSeats = car.getNumberOfSeats();
            this.score = score;
            this.recommendationType = type;
            this.explanation = generateExplanation(type, score);
        }

        public CarRecommendation(Car car, double score, String type, String customExplanation) {
            this.carId = car.getIdCar();
            this.carName = car.getNameCar();
            this.avatarCar = car.getAvatarCar();
            this.price = car.getPrice();
            this.promotionalPrice = car.getPromotionalPrice();
            this.numberOfSeats = car.getNumberOfSeats();
            this.score = score;
            this.recommendationType = type;
            this.explanation = customExplanation;
        }

        private String generateExplanation(String type, double score) {
            switch (type) {
                case "collaborative":
                    if (score > 0.7) {
                        return "🔥 Rất nhiều người có sở thích giống bạn đã chọn xe này";
                    } else if (score > 0.4) {
                        return "👥 Người dùng có sở thích tương tự cũng thích xe này";
                    } else {
                        return "🎯 Được gợi ý dựa trên sở thích của người dùng tương tự";
                    }
                case "content-based":
                    if (score > 0.7) {
                        return "✨ Xe này rất giống với các xe bạn đã xem gần đây";
                    } else if (score > 0.5) {
                        return "🔍 Có tính năng tương tự các xe bạn quan tâm";
                    } else {
                        return "💡 Phù hợp với tiêu chí bạn thường tìm kiếm";
                    }
                case "popular":
                    return "🌟 Xe phổ biến được nhiều người quan tâm";
                default:
                    return "🤖 Được AI gợi ý cho bạn";
            }
        }

        // Getters
        public int getCarId() {
            return carId;
        }

        public String getCarName() {
            return carName;
        }

        public String getAvatarCar() {
            return avatarCar;
        }

        public int getPrice() {
            return price;
        }

        public int getPromotionalPrice() {
            return promotionalPrice;
        }

        public int getNumberOfSeats() {
            return numberOfSeats;
        }

        public double getScore() {
            return score;
        }

        public String getRecommendationType() {
            return recommendationType;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
