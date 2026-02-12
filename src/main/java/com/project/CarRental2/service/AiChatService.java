package com.project.CarRental2.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class AiChatService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiKey;
    private final String model;
    private final String apiProvider;
    private final com.project.CarRental2.repository.CarRepository carRepository;
    private long lastContextUpdate = 0;
    private String cachedContext = "";
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

    public AiChatService(com.project.CarRental2.repository.CarRepository carRepository,
            @Value("${ai.api.key:}") String apiKey,
            @Value("${ai.model:openai}") String model,
            @Value("${ai.provider:pollinations}") String apiProvider) {
        this.carRepository = carRepository;
        this.apiKey = apiKey;
        this.model = model;
        this.apiProvider = apiProvider;
        System.out.println("[AI Service] Initialized with provider: " + this.apiProvider + ", model: " + this.model);
        System.out.println("[AI Service] API Key configured: "
                + (this.apiKey != null && !this.apiKey.isBlank() ? "YES" : "NO (using free API)"));
    }

    private String getSystemContext() {
        if (System.currentTimeMillis() - lastContextUpdate < CACHE_DURATION && !cachedContext.isEmpty()) {
            System.out.println("[AI Service] Using cached inventory context (age: "
                    + (System.currentTimeMillis() - lastContextUpdate) / 1000 + "s)");
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
                        (c.getPromotionalPrice() > 0 ? (c.getPrice() - (c.getPrice() * c.getPromotionalPrice() / 100))
                                : c.getPrice()),
                        c.getNumberOfSeats(), c.getAddressCar().split(",")[0],
                        features.toString()));
            }
            sb.append("[[END INVENTORY]]\n");
            cachedContext = sb.toString();
            lastContextUpdate = System.currentTimeMillis();
            return cachedContext;
        } catch (Exception e) {
            System.err.println("[AI Service] Error fetching context: " + e.getMessage());
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

        // Price inquiry
        if (lower.contains("giá") || lower.contains("bao nhiêu") || lower.contains("phí")
                || lower.contains("chi phí")) {
            return "💰 **Bảng giá tham khảo tại MIOTO:**\n\n" +
                    "• Xe 4 chỗ: 500.000 - 800.000đ/ngày\n" +
                    "• Xe 5-7 chỗ: 700.000 - 1.200.000đ/ngày\n" +
                    "• Xe 16 chỗ: 1.000.000 - 1.500.000đ/ngày\n\n" +
                    "Giá đã bao gồm bảo hiểm cơ bản. Giá có thể thay đổi theo mẫu xe và thời điểm thuê.\n\n" +
                    "👉 Xem chi tiết tại trang **Tìm Xe** để biết giá chính xác!";
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

    public String getReply(String prompt) {
        System.out.println(
                "[AI Service] Processing request: " + prompt.substring(0, Math.min(50, prompt.length())) + "...");

        try {
            if ("pollinations".equalsIgnoreCase(this.apiProvider)) {
                return callPollinationsAPI(prompt);
            } else if ("openrouter".equalsIgnoreCase(this.apiProvider)) {
                return callOpenRouterAPI(prompt);
            } else if ("gemini".equalsIgnoreCase(this.apiProvider)) {
                return callGeminiAPI(prompt);
            } else {
                // Default to pollinations (free, no key needed)
                return callPollinationsAPI(prompt);
            }
        } catch (Exception e) {
            System.err.println("[AI Service] General Error: " + e.getMessage());
            e.printStackTrace();
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call Pollinations.ai API - FREE, NO API KEY REQUIRED!
     * https://pollinations.ai - Open source AI platform
     */
    private String callPollinationsAPI(String prompt) {
        System.out.println("[Pollinations] Calling FREE API (no key required)...");

        try {
            // Pollinations uses OpenAI-compatible API format
            String url = "https://text.pollinations.ai/openai";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build message with system context
            List<Map<String, String>> messages = new ArrayList<>();

            // System message
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");

            String context = getSystemContext();
            String systemContent = "Bạn là trợ lý AI của MIOTO - website cho thuê xe ô tô tự lái hàng đầu Việt Nam.\n\n"
                    + "QUY TẮC QUAN TRỌNG:\n"
                    + "1. Luôn trả lời bằng tiếng Việt, ngắn gọn, thân thiện.\n"
                    + "2. Tập trung vào việc hỗ trợ khách hàng thuê xe.\n"
                    + "3. Khi được hỏi về xe cụ thể, kiểm tra danh sách xe bên dưới.\n"
                    + "4. Nếu xe KHÔNG CÓ trong danh sách, hãy nói: 'MIOTO hiện chưa có mẫu xe này.'\n"
                    + "5. Với câu hỏi không liên quan đến thuê xe, trả lời ngắn gọn và hướng về dịch vụ thuê xe.\n\n"
                    + "THÔNG TIN DỊCH VỤ:\n"
                    + "- Website: MIOTO.vn\n"
                    + "- Dịch vụ: Cho thuê xe tự lái và có tài xế\n"
                    + "- Giá xe 4 chỗ: 500.000 - 800.000đ/ngày\n"
                    + "- Giá xe 7 chỗ: 700.000 - 1.200.000đ/ngày\n"
                    + "- Yêu cầu: CMND/CCCD + Bằng lái xe (cho thuê tự lái)\n\n"
                    + "DANH SÁCH XE HIỆN CÓ:\n" + context + "\n"
                    + "Hãy trả lời câu hỏi của khách hàng một cách hữu ích nhất.";

            systemMsg.put("content", systemContent);
            messages.add(systemMsg);

            // User message
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", this.model); // "openai" is the default
            body.put("messages", messages);
            body.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            System.out.println("[Pollinations] Response status: " + response.getStatusCode());

            Map<?, ?> resp = response.getBody();
            if (resp == null) {
                System.out.println("[Pollinations] ERROR: Response body is null");
                return getFallbackResponse(prompt);
            }

            // Parse OpenAI-compatible response format
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> choices = (List<Map<?, ?>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                System.out.println("[Pollinations] ERROR: No choices in response");
                return getFallbackResponse(prompt);
            }

            Map<?, ?> choice = choices.get(0);
            @SuppressWarnings("unchecked")
            Map<?, ?> message = (Map<?, ?>) choice.get("message");
            if (message != null && message.get("content") != null) {
                String reply = message.get("content").toString();
                System.out.println("[Pollinations] SUCCESS: Got reply (" + reply.length() + " chars)");
                return reply;
            }

            System.out.println("[Pollinations] ERROR: No content in response");
            return getFallbackResponse(prompt);

        } catch (HttpClientErrorException e) {
            System.err.println("[Pollinations] HTTP Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            System.err.println("[Pollinations] Error: " + e.getMessage());
            // Try alternative simple GET endpoint
            return callPollinationsSimpleAPI(prompt);
        }
    }

    /**
     * Alternative simple Pollinations API endpoint (GET request)
     */
    private String callPollinationsSimpleAPI(String prompt) {
        try {
            System.out.println("[Pollinations] Trying simple GET API...");

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
                System.out.println("[Pollinations Simple] SUCCESS: Got reply (" + response.length() + " chars)");
                return response;
            }

            return getFallbackResponse(prompt);
        } catch (Exception e) {
            System.err.println("[Pollinations Simple] Error: " + e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call OpenRouter API (requires API key)
     */
    private String callOpenRouterAPI(String prompt) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            System.out.println("[OpenRouter] No API key, falling back to Pollinations");
            return callPollinationsAPI(prompt);
        }

        String url = "https://openrouter.ai/api/v1/chat/completions";
        System.out.println("[OpenRouter] Calling API with model: " + this.model);

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
                "2. Nếu xe không có trong danh sách, hãy nói rõ là hệ thống chưa có mẫu này, sau đó mới tư vấn thông tin chung.\n"
                +
                "3. Luôn giữ phong cách chuyên nghiệp và thân thiện.";
        systemMsg.put("content", systemContent);
        messages.add(systemMsg);

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
                        return message.get("content").toString();
                    }
                }
            }
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            System.err.println("[OpenRouter] Error: " + e.getMessage());
            return getFallbackResponse(prompt);
        }
    }

    /**
     * Call Gemini API (requires API key)
     */
    private String callGeminiAPI(String prompt) {
        if (this.apiKey == null || this.apiKey.isBlank()) {
            System.out.println("[Gemini] No API key, falling back to Pollinations");
            return callPollinationsAPI(prompt);
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + this.model
                + ":generateContent?key=" + this.apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String context = getSystemContext();
        String fullPrompt = "Bạn là trợ lý AI của website CarRental.\n\n"
                + "DANH SÁCH XE HỆ THỐNG:\n" + context + "\n\n"
                + "HƯỚNG DẪN TRẢ LỜI:\n"
                + "1. Nếu người dùng hỏi về xe KHÔNG CÓ trong danh sách trên, hãy khẳng định: 'Hệ thống CarRental hiện chưa có xe này' trước khi cung cấp thêm kiến thức chung.\n"
                + "2. Nếu xe CÓ trong danh sách, hãy tư vấn chi tiết dựa trên các tính năng và giá đã cung cấp.\n"
                + "3. Trả lời bằng Markdown để dễ đọc.\n\n"
                + "Câu hỏi người dùng: " + prompt;

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
                            return parts.get(0).get("text").toString();
                        }
                    }
                }
            }
            return getFallbackResponse(prompt);
        } catch (Exception e) {
            System.err.println("[Gemini] Error: " + e.getMessage());
            return getFallbackResponse(prompt);
        }
    }
}
