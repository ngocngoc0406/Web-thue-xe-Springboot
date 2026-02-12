package com.project.CarRental2.controller;

import java.time.Instant;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.project.CarRental2.api.dto.ChatMessage;
import com.project.CarRental2.api.dto.RecommendationResult;
import com.project.CarRental2.service.AiChatService;
import com.project.CarRental2.service.AiRecommendationService;
import com.project.CarRental2.service.ChatParser;

@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final AiChatService aiChatService;
    private final AiRecommendationService aiRecommendationService;
    private final ChatParser chatParser;

    public ChatController(SimpMessagingTemplate messagingTemplate, AiChatService aiChatService,
            AiRecommendationService aiRecommendationService, ChatParser chatParser) {
        this.messagingTemplate = messagingTemplate;
        this.aiChatService = aiChatService;
        this.aiRecommendationService = aiRecommendationService;
        this.chatParser = chatParser;
    }

    @GetMapping("/chat")
    public String chatPage() {
        return "pages/chat"; // templates/pages/chat.html
    }

    @MessageMapping("/chat")
    public void handle(ChatMessage message, org.springframework.messaging.simp.SimpMessageHeaderAccessor sha) {
        message.setTimestamp(Instant.now().toString());
        // log message receipt
        System.out.println("[chat] received from " + message.getSender() + ": " + message.getContent() + " askAi="
                + message.getAskAi() + " session=" + sha.getSessionId());

        // broadcast user message to public topic
        messagingTemplate.convertAndSend("/topic/messages", message);

        // if askAi flag set, call AI and send reply privately to the session that sent
        // it
        if (Boolean.TRUE.equals(message.getAskAi())) {
            String sessionId = sha.getSessionId();
            System.out.println("[chat] asking AI for session=" + sessionId);
            // notify only this user that AI is typing
            ChatMessage typing = new ChatMessage();
            typing.setSender("AI");
            typing.setType("TYPING");
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", typing, createHeaders(sessionId));

            new Thread(() -> {
                // Check if message is car-related and use internal recommendation service
                if (isCarRelatedQuery(message.getContent()) && !isInformationQuery(message.getContent())) {
                    System.out.println("[chat] Processing car recommendation for session=" + sessionId);
                    try {
                        var req = chatParser.parse(message.getContent(), 5); // top 5 recommendations
                        var results = aiRecommendationService.recommend(req);

                        if (results != null && !results.isEmpty()) {
                            // Send recommendation results
                            StringBuilder response = new StringBuilder(
                                    "Dựa trên yêu cầu của bạn, tôi gợi ý các xe sau:\n\n");
                            for (int i = 0; i < Math.min(results.size(), 3); i++) {
                                RecommendationResult car = results.get(i);
                                response.append(String.format("%d. %s\n", i + 1, car.getNameCar()))
                                        .append(String.format("   Giá: %s VND/ngày\n", formatPrice(car.getPrice())))
                                        .append(String.format("   Số chỗ: %d\n", car.getNumberOfSeats()))
                                        .append(String.format("   Link: /car-detail/%d\n\n", car.getIdCar()));
                            }

                            if (results.size() > 3) {
                                response.append("Xem thêm xe khác tại: /filter-car");
                            }

                            ChatMessage aiMsg = new ChatMessage();
                            aiMsg.setSender("AI");
                            aiMsg.setContent(response.toString());
                            aiMsg.setType("CHAT");
                            aiMsg.setTimestamp(Instant.now().toString());
                            messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", aiMsg,
                                    createHeaders(sessionId));
                        } else {
                            // FALLBACK: If no recommendation found, try general AI chat
                            System.out.println(
                                    "[chat] No recommendations found, falling back to LLM for session=" + sessionId);
                            String reply = aiChatService.getReply(message.getContent());
                            sendAiReply(sessionId, reply);
                        }
                    } catch (Exception e) {
                        System.err.println("[chat] Error processing car recommendation: " + e.getMessage());
                        // Fallback to LLM on error
                        String reply = aiChatService.getReply(message.getContent());
                        sendAiReply(sessionId, reply);
                    }
                } else {
                    // Use external AI service for non-car queries or information queries
                    String reply = aiChatService.getReply(message.getContent());
                    System.out.println("[chat] AI reply for session=" + sessionId + " => "
                            + (reply != null ? reply.substring(0, Math.min(20, reply.length())) + "..." : "null"));
                    sendAiReply(sessionId, reply);
                }
                // DEBUG: also publish to public topic so we can verify replies are produced
                // messagingTemplate.convertAndSend("/topic/messages", aiMsg);
            }).start();
        }
    }

    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        org.springframework.messaging.simp.SimpMessageHeaderAccessor headerAccessor = org.springframework.messaging.simp.SimpMessageHeaderAccessor
                .create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }

    private boolean isInformationQuery(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        // Expand keywords for information-seeking queries
        return lower.contains("thông tin") || lower.contains("đánh giá") ||
                lower.contains("ưu điểm") || lower.contains("nhược điểm") ||
                lower.contains("so sánh") || lower.contains("ý kiến") ||
                lower.contains("mô tả") || lower.contains("review") ||
                lower.contains("info") || lower.contains("thông số") ||
                lower.contains("kỹ thuật") || lower.contains("động cơ") ||
                lower.contains("màu sắc") || lower.contains("nội thất") ||
                lower.contains("ngoại thất") || lower.contains("tiêu thụ");
    }

    private void sendAiReply(String sessionId, String reply) {
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSender("AI");
        aiMsg.setTimestamp(Instant.now().toString());

        if (reply != null && reply.startsWith("REDIRECT:")) {
            String[] parts = reply.substring(9).split(":");
            if (parts.length >= 4) {
                String address = parts[0];
                String dateStart = parts[1];
                String dateEnd = parts[2];
                String driver = parts[3];
                String filterUrl = "/filter-car?address=" + address + "&dateStart=" + dateStart
                        + "&dateEnd=" + dateEnd + "&driver=" + driver;
                aiMsg.setContent(filterUrl);
                aiMsg.setType("URL");
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", aiMsg, createHeaders(sessionId));

                ChatMessage redirectMsg = new ChatMessage();
                redirectMsg.setSender("AI");
                redirectMsg.setContent("Tôi đã tìm thấy kết quả cho bạn. Nhấn vào link dưới đây để xem.");
                redirectMsg.setType("CHAT");
                redirectMsg.setTimestamp(Instant.now().toString());
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", redirectMsg,
                        createHeaders(sessionId));
            } else {
                aiMsg.setContent("Xin lỗi, tôi không thể xử lý yêu cầu của bạn.");
                aiMsg.setType("CHAT");
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", aiMsg, createHeaders(sessionId));
            }
        } else {
            aiMsg.setContent(reply != null ? reply : "Xin lỗi, tôi không thể trả lời lúc này.");
            aiMsg.setType("CHAT");
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", aiMsg, createHeaders(sessionId));
        }
    }

    private boolean isCarRelatedQuery(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        // Only consider car-related if message explicitly mentions car/rental keywords
        // Must contain car-related words, not just generic location/time words
        boolean hasCarKeyword = lower.contains("xe") || lower.contains("car") ||
                lower.contains("thuê xe") || lower.contains("rental") ||
                lower.contains("ô tô") || lower.contains("auto");
        boolean hasRentalContext = lower.contains("thuê") || lower.contains("mướn") ||
                lower.contains("rent") || lower.contains("đặt xe") ||
                lower.contains("giá xe") || lower.contains("xe mấy chỗ");

        return hasCarKeyword || hasRentalContext;
    }

    private String formatPrice(int price) {
        if (price >= 1000000) {
            return String.format("%,d", price / 1000) + "k";
        } else if (price >= 1000) {
            return String.format("%,dk", price / 1000);
        } else {
            return String.format("%,d", price);
        }
    }
}
