package com.project.CarRental2.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String apiProvider;
    private final com.project.CarRental2.repository.CarRepository carRepository;
    private long lastContextUpdate = 0;
    private String cachedContext = "";
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes
    private final Map<String, List<Map<String, String>>> conversationHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 10; // Keep last 10 messages

    public AiChatService(com.project.CarRental2.repository.CarRepository carRepository,
            @Value("${ai.api.key:}") String apiKey,
            @Value("${ai.model:openai}") String model,
            @Value("${ai.provider:pollinations}") String apiProvider) {
        this.carRepository = carRepository;
        this.apiKey = apiKey;
        this.model = model;
        this.apiProvider = apiProvider;

        // Configure RestTemplate with 15-second timeout (LLM APIs need 8-12s)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);

        log.info("[AI Service] Initialized with provider: {}, model: {}", this.apiProvider, this.model);
        log.info("[AI Service] API Key configured: {}",
                (this.apiKey != null && !this.apiKey.isBlank() ? "YES" : "NO (using free API)"));
    }

    private String getSystemContext() {
        if (System.currentTimeMillis() - lastContextUpdate < CACHE_DURATION && !cachedContext.isEmpty()) {
            log.debug("[AI Service] Using cached inventory context (age: {}s)",
                    (System.currentTimeMillis() - lastContextUpdate) / 1000);
            return cachedContext;
        }
        try {
            List<com.project.CarRental2.model.Car> cars = carRepository.getAllCarOrderByNameCarAsc();
            if (cars == null || cars.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n[[CURRENT CAR INVENTORY]]\n");
            for (com.project.CarRental2.model.Car c : cars) {
                // Formatting: ID - Name - Price - Seats - Location - Status - Features
                StringBuilder features = new StringBuilder();
                if (c.isBluetooth())
                    features.append("BT,");
                if (c.isGpsLocator() || c.isMaps())
                    features.append("GPS,");
                if (c.isReverseCamera())
                    features.append("CamL,");
                if (c.isDashCamera())
                    features.append("CamH,");
                if (c.isCamera360())
                    features.append("360,");
                if (c.isSunroof())
                    features.append("SunRf,");
                if (c.isUsb())
                    features.append("USB,");
                if (c.isAirbags())
                    features.append("AirB,");
                if (c.isManualTransmissionCar())
                    features.append("Sàn,");
                else
                    features.append("TĐ,");

                sb.append(String.format("- ID:%d|%s|%dđ|%dC|%s|%s\n",
                        c.getIdCar(), c.getNameCar(),
                        c.getFinalPrice(),
                        c.getNumberOfSeats(), c.getAddressCar().split(",")[0],
                        features.toString()));
            }
            sb.append("[[END INVENTORY]]\n");
            cachedContext = sb.toString();
            lastContextUpdate = System.currentTimeMillis();
            return cachedContext;
        } catch (Exception e) {
            log.error("[AI Service] Error fetching context: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Fallback response when AI API is unavailable
     */
    private String getFallbackResponse(String prompt) {
        String lower = prompt.toLowerCase();

        // Greetings
        if (lower.contains("xin chào") || lower.contains("hello") || lower.contains("hi") || lower.contains("chào")
                || lower.contains("hey")) {
            return "Xin chào! 👋 Tôi là trợ lý AI của MIOTO.\n\n" +
                    "Tôi có thể giúp bạn:\n" +
                    "• Tìm xe phù hợp với nhu cầu\n" +
                    "• Giải đáp về quy trình thuê xe\n" +
                    "• Thông tin về giá cả và ưu đãi\n\n" +
                    "Bạn cần tìm xe ở đâu và khi nào ạ?";
        }

        // Thanks
        if (lower.contains("cảm ơn") || lower.contains("thank") || lower.contains("thanks")) {
            return "Không có gì ạ! 😊 Nếu bạn cần hỗ trợ thêm về việc thuê xe, cứ hỏi tôi nhé!";
        }

        // Price inquiry - use real data from database
        if (lower.contains("giá") || lower.contains("bao nhiêu") || lower.contains("phí")
                || lower.contains("chi phí")) {
            try {
                List<com.project.CarRental2.model.Car> cars = carRepository.getAllCarOrderByNameCarAsc();
                if (cars != null && !cars.isEmpty()) {
                    // Group by seat count and find price ranges
                    java.util.Map<Integer, List<com.project.CarRental2.model.Car>> bySeats = new java.util.LinkedHashMap<>();
                    for (com.project.CarRental2.model.Car c : cars) {
                        bySeats.computeIfAbsent(c.getNumberOfSeats(), k -> new ArrayList<>()).add(c);
                    }

                    StringBuilder priceResponse = new StringBuilder();
                    priceResponse.append("💰 **Bảng giá thuê xe thực tế tại MIOTO:**\n\n");

                    for (java.util.Map.Entry<Integer, List<com.project.CarRental2.model.Car>> entry : bySeats.entrySet()) {
                        List<com.project.CarRental2.model.Car> group = entry.getValue();
                        int minPrice = group.stream().mapToInt(com.project.CarRental2.model.Car::getFinalPrice).min().orElse(0);
                        int maxPrice = group.stream().mapToInt(com.project.CarRental2.model.Car::getFinalPrice).max().orElse(0);
                        String exampleName = group.get(0).getNameCar();

                        priceResponse.append(String.format("• **Xe %d chỗ**: %,dK - %,dK/ngày (VD: %s)\n",
                                entry.getKey(), minPrice, maxPrice, exampleName));
                    }

                    priceResponse.append("\nGiá đã bao gồm bảo hiểm cơ bản. Giá có thể thay đổi theo mẫu xe.\n\n");
                    priceResponse.append("👉 Bạn muốn tìm xe ở khu vực nào? Hãy cho tôi biết để tư vấn chính xác hơn!");
                    return priceResponse.toString();
                }
            } catch (Exception e) {
                log.error("[Fallback] Error fetching price data: {}", e.getMessage());
            }
            // If database fails, still give a generic answer
            return "💰 Giá thuê xe tại MIOTO phụ thuộc vào loại xe và thời điểm thuê.\n\n" +
                    "👉 Bạn muốn thuê xe ở khu vực nào? Cho tôi biết để tư vấn giá chính xác nhé!";
        }

        // Booking process
        if (lower.contains("thuê") || lower.contains("đặt") || lower.contains("booking") || lower.contains("cách")) {
            return "📋 **Quy trình thuê xe MIOTO:**\n\n" +
                    "1️⃣ Chọn xe phù hợp trên website\n" +
                    "2️⃣ Chọn ngày nhận và trả xe\n" +
                    "3️⃣ Điền thông tin và thanh toán đặt cọc\n" +
                    "4️⃣ Nhận xe tại địa điểm đã chọn\n\n" +
                    "📌 **Giấy tờ cần có:**\n" +
                    "• CMND/CCCD (bản gốc)\n" +
                    "• Bằng lái xe B2 trở lên (với thuê tự lái)\n" +
                    "• Hộ khẩu hoặc KT3 (một số chủ xe yêu cầu)";
        }

        // Location inquiry
        if (lower.contains("địa chỉ") || lower.contains("ở đâu") || lower.contains("chi nhánh")
                || lower.contains("địa điểm")) {
            return "📍 MIOTO có xe cho thuê tại nhiều tỉnh thành trên cả nước!\n\n" +
                    "Bạn có thể sử dụng thanh tìm kiếm trên trang chủ để lọc xe theo địa điểm mong muốn.\n\n" +
                    "Hãy cho tôi biết bạn muốn thuê xe ở tỉnh/thành phố nào nhé?";
        }

        // Driver option
        if (lower.contains("tài xế") || lower.contains("driver") || lower.contains("lái xe")) {
            return "🚗 **Hai lựa chọn cho bạn:**\n\n" +
                    "**1. Thuê tự lái:**\n" +
                    "• Bạn tự lái xe\n" +
                    "• Yêu cầu bằng lái B2 trở lên\n" +
                    "• Giá thường rẻ hơn\n\n" +
                    "**2. Thuê có tài xế:**\n" +
                    "• MIOTO cung cấp tài xế chuyên nghiệp\n" +
                    "• Phù hợp cho đám cưới, đi công tác\n" +
                    "• Giá bao gồm lương + xăng của tài xế\n\n" +
                    "Bạn muốn thuê theo hình thức nào?";
        }

        // Car types
        if (lower.contains("4 chỗ") || lower.contains("5 chỗ") || lower.contains("7 chỗ") || lower.contains("16 chỗ")) {
            return "🚙 Bạn đang tìm xe phù hợp!\n\n" +
                    "Hãy cho tôi biết thêm:\n" +
                    "• Bạn muốn thuê ở đâu?\n" +
                    "• Thời gian thuê từ ngày nào đến ngày nào?\n" +
                    "• Cần xe tự lái hay có tài xế?\n\n" +
                    "Hoặc bạn có thể vào trang **Tìm Xe** để lọc theo nhu cầu!";
        }

        // Promotion
        if (lower.contains("khuyến mãi") || lower.contains("giảm giá") || lower.contains("ưu đãi")
                || lower.contains("voucher")) {
            return "🎉 **Ưu đãi tại MIOTO:**\n\n" +
                    "• Giảm giá cho thuê dài ngày (từ 3 ngày trở lên)\n" +
                    "• Giảm 5-10% cho khách hàng thường xuyên\n" +
                    "• Chương trình giới thiệu bạn bè\n\n" +
                    "Kiểm tra các xe có biểu tượng **SALE** trên trang Tìm Xe để xem ưu đãi cụ thể!";
        }

        // Default response
        return "Cảm ơn bạn đã liên hệ MIOTO! 😊\n\n" +
                "Tôi có thể giúp bạn:\n" +
                "• Tìm xe phù hợp\n" +
                "• Thông tin giá thuê\n" +
                "• Hướng dẫn đặt xe\n" +
                "• Giải đáp thắc mắc\n\n" +
                "Hãy cho tôi biết bạn cần hỗ trợ gì nhé!";
    }

    public String getReply(String prompt, String sessionId, Map<String, String> metadata) {
        log.info("[AI Service] Processing request for session {}: {}...",
                sessionId, prompt.substring(0, Math.min(50, prompt.length())));

        try {
            if ("pollinations".equalsIgnoreCase(this.apiProvider)) {
                return callPollinationsAPI(prompt, sessionId, metadata);
            } else if ("openrouter".equalsIgnoreCase(this.apiProvider)) {
                return callOpenRouterAPI(prompt, sessionId, metadata);
            } else if ("gemini".equalsIgnoreCase(this.apiProvider)) {
                return callGeminiAPI(prompt, sessionId, metadata);
            } else {
                // Default to pollinations (free, no key needed)
                return callPollinationsAPI(prompt, sessionId, metadata);
            }
        } catch (Exception e) {
            log.error("[AI Service] General Error for session {}: {}", sessionId, e.getMessage(), e);
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call Pollinations.ai API - FREE, NO API KEY REQUIRED!
     * https://pollinations.ai - Open source AI platform
     */
    private String callPollinationsAPI(String prompt, String sessionId, Map<String, String> metadata) {
        log.info("[Pollinations] Calling FREE API for session {} with metadata...", sessionId);

        try {
            // Pollinations uses OpenAI-compatible API format
            String url = "https://text.pollinations.ai/openai";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build messages including history
            List<Map<String, String>> messages = new ArrayList<>();

            // 1. System message
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");

            String context = getSystemContext();
            String systemContent = "Bạn là trợ lý AI chính thức của MIOTO - nền tảng cho thuê xe ô tô tự lái hàng đầu Việt Nam.\n\n"
                    + "CÁCH TRẢ LỜI:\n"
                    + "1. Luôn trả lời bằng tiếng Việt, thân thiện, chuyên nghiệp.\n"
                    + "2. Sử dụng Markdown để trình bày (in đậm, danh sách).\n"
                    + "3. Khi khách hỏi tìm xe, hãy xem danh sách xe bên dưới. Nếu không có mẫu đó, gợi ý xe tương tự thuộc cùng phân khúc.\n"
                    + "4. Nếu chưa biết địa điểm hoặc thời gian khách muốn thuê, hãy khéo léo hỏi thêm để tư vấn chính xác hơn.\n"
                    + "5. Trả lời ngắn gọn, tập trung vào giá trị cho khách hàng.\n\n"
                    + "DANH SÁCH XE MIOTO HIỆN CÓ:\n" + context + "\n\n"
                    + "NGỮ CẢNH TRANG HIỆN TẠI:\n" + formatMetadata(metadata) + "\n\n"
                    + "Hãy nhớ bạn đang trò chuyện trực tiếp với khách hàng. Hãy trả lời câu hỏi mới nhất dựa trên lịch sử trò chuyện phía dưới.";

            systemMsg.put("content", systemContent);
            messages.add(systemMsg);

            // 2. History context
            List<Map<String, String>> history = conversationHistory.getOrDefault(sessionId, new ArrayList<>());
            messages.addAll(history);

            // 3. Current User message
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", this.model);
            body.put("messages", messages);
            body.put("max_tokens", 800);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<?, ?> resp = response.getBody();

            if (resp != null) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> choices = (List<Map<?, ?>>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> choice = choices.get(0);
                    @SuppressWarnings("unchecked")
                    Map<?, ?> messageResp = (Map<?, ?>) choice.get("message");
                    if (messageResp != null && messageResp.get("content") != null) {
                        String reply = messageResp.get("content").toString();

                        // Update history
                        updateHistory(sessionId, prompt, reply);

                        return reply;
                    }
                }
            }
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            log.error("[Pollinations] Error for session {}: {}", sessionId, e.getMessage());
            return callPollinationsSimpleAPI(prompt);
        }
    }

    public void updateHistory(String sessionId, String prompt, String reply) {
        List<Map<String, String>> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

        Map<String, String> userEntry = new HashMap<>();
        userEntry.put("role", "user");
        userEntry.put("content", prompt);

        Map<String, String> aiEntry = new HashMap<>();
        aiEntry.put("role", "assistant");
        aiEntry.put("content", reply);

        history.add(userEntry);
        history.add(aiEntry);

        // Keep only last N messages
        if (history.size() > MAX_HISTORY_SIZE * 2) {
            conversationHistory.put(sessionId, new ArrayList<>(history.subList(history.size() - MAX_HISTORY_SIZE * 2, history.size())));
        }
    }

    /**
     * Alternative simple Pollinations API endpoint (GET request)
     */
    private String callPollinationsSimpleAPI(String prompt) {
        try {
            log.info("[Pollinations] Trying simple GET API...");

            // Add context to the prompt
            String context = getSystemContext(); // Note: Simple GET API might fail if URL too long
            String fullPrompt = "Bạn là trợ lý AI của website CarRental.\n\n"
                    + "DANH SÁCH XE HỆ THỐNG:\n" + context + "\n\n"
                    + "HƯỚNG DẪN:\n"
                    + "- Nếu xe người dùng hỏi KHÔNG CÓ trong danh sách trên, hãy báo 'Hệ thống hiện chưa có mẫu này' trước.\n"
                    + "- Nếu CÓ, hãy tư vấn chi tiết.\n"
                    + "\nCâu hỏi người dùng: " + prompt;

            if (fullPrompt.length() > 1500) {
                // Truncate if too long for GET request
                fullPrompt = fullPrompt.substring(0, 1500) + "... (truncated)";
            }

            String encodedPrompt = URLEncoder.encode(fullPrompt, StandardCharsets.UTF_8.toString());
            String url = "https://text.pollinations.ai/" + encodedPrompt;

            String response = restTemplate.getForObject(url, String.class);

            if (response != null && !response.isBlank()) {
                log.info("[Pollinations Simple] Got reply ({} chars)", response.length());
                return response;
            }

            return getFallbackResponse(prompt);
        } catch (Exception e) {
            log.error("[Pollinations Simple] Error: {}", e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call OpenRouter API (requires API key)
     */
    private String callOpenRouterAPI(String prompt, String sessionId, Map<String, String> metadata) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            log.info("[OpenRouter] No API key, falling back to Pollinations");
            return callPollinationsAPI(prompt, sessionId, metadata);
        }

        String url = "https://openrouter.ai/api/v1/chat/completions";
        log.info("[OpenRouter] Calling API with model for session {}: {}", sessionId, this.model);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + this.apiKey);
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "CarRental AI Assistant");

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");

        String context = getSystemContext();
        String systemContent = "Bạn là trợ lý AI của CarRental. Bạn có kiến thức rộng về ô tô.\n" +
                "1. Danh sách xe HIỆN CÓ tại hệ thống:\n" + context + "\n" +
                "2. Nếu xe không có trong danh sách, hãy nói rõ là hệ thống chưa có mẫu này, sau đó mới tư vấn thông tin chung.\n" +
                "3. NGỮ CẢNH TRANG HIỆN TẠI: " + formatMetadata(metadata) + "\n" +
                "4. Luôn giữ phong cách chuyên nghiệp và thân thiện.";
        systemMsg.put("content", systemContent);
        messages.add(systemMsg);

        // History context
        List<Map<String, String>> history = conversationHistory.getOrDefault(sessionId, new ArrayList<>());
        messages.addAll(history);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        Map<String, Object> body = new HashMap<>();
        body.put("model", this.model);
        body.put("messages", messages);
        body.put("max_tokens", 500);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<?, ?> resp = response.getBody();
            if (resp != null) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> choices = (List<Map<?, ?>>) resp.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<?, ?> message = (Map<?, ?>) choices.get(0).get("message");
                    if (message != null && message.get("content") != null) {
                        String reply = message.get("content").toString();
                        updateHistory(sessionId, prompt, reply);
                        return reply;
                    }
                }
            }
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            log.error("[OpenRouter] Error: {}", e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call Gemini API (requires API key)
     */
    private String callGeminiAPI(String prompt, String sessionId, Map<String, String> metadata) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            log.info("[Gemini] No API key, falling back to Pollinations");
            return callPollinationsAPI(prompt, sessionId, metadata);
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + this.model
                + ":generateContent?key=" + this.apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String context = getSystemContext();
        
        // Build history context for Gemini
        StringBuilder historyContent = new StringBuilder();
        List<Map<String, String>> history = conversationHistory.getOrDefault(sessionId, new ArrayList<>());
        for (Map<String, String> msg : history) {
            historyContent.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
        }
        String fullPrompt = "Bạn là trợ lý AI của CarRental. Bạn có kiến thức rộng về ô tô.\n"
                + "DANH SÁCH XE HIỆN CÓ TẠI HỆ THỐNG:\n" + context + "\n"
                + "NGỮ CẢNH TRANG HIỆN TẠI: " + formatMetadata(metadata) + "\n"
                + "PHẦN TRÒ CHUYỆN TRƯỚC ĐÓ:\n" + historyContent.toString() + "\n"
                + "HƯỚNG DẪN TRẢ LỜI:\n"
                + "1. Nếu người dùng hỏi về xe KHÔNG CÓ trong danh sách trên, hãy khẳng định: 'Hệ thống CarRental hiện chưa có xe này' trước khi cung cấp thêm kiến thức chung.\n"
                + "2. Nếu xe CÓ trong danh sách, hãy tư vấn chi tiết dựa trên các tính năng và giá đã cung cấp.\n"
                + "3. Trả lời bằng Markdown để dễ đọc.\n\n"
                + "Câu hỏi mới của người dùng: " + prompt;
        Map<String, Object> part = new HashMap<>();
        part.put("text", fullPrompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(content));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<?, ?> resp = response.getBody();
            if (resp != null) {
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> candidates = (List<Map<?, ?>>) resp.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<?, ?> contentResp = (Map<?, ?>) candidates.get(0).get("content");
                    if (contentResp != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<?, ?>> parts = (List<Map<?, ?>>) contentResp.get("parts");
                        if (parts != null && !parts.isEmpty() && parts.get(0).get("text") != null) {
                            String reply = parts.get(0).get("text").toString();
                            updateHistory(sessionId, prompt, reply);
                            return reply;
                        }
                    }
                }
            }
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            log.error("[Gemini] Error: {}", e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    private String formatMetadata(java.util.Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return "Không có thông tin trang hiện tại.";
        StringBuilder sb = new StringBuilder();
        metadata.forEach((k, v) -> sb.append(k).append(": ").append(v).append(", "));
        return sb.toString();
    }
}
