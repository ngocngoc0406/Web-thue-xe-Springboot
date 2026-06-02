package com.project.CarRental2.controller;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

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
        String sessionId = sha.getSessionId();
        
        // Identify the user from the HTTP Session (passed via Handshake Interceptor)
        Object userObj = sha.getSessionAttributes().get("sesionUser");
        String identityKey = "guest";
        if (userObj instanceof com.project.CarRental2.model.User) {
            identityKey = "user_" + ((com.project.CarRental2.model.User) userObj).getIdUser();
        }
        
        message.setTimestamp(Instant.now().toString());
        log.info("[WEBSOCKET] Received message from {} (ID: {}): type={} content='{}' (Session: {})",
                message.getSender(), identityKey, message.getType(), message.getContent(), sessionId);

        // broadcast user message to public topic (optional, depending on requirements)
        // messagingTemplate.convertAndSend("/topic/messages", message);

        if (Boolean.TRUE.equals(message.getAskAi())) {
            log.info("[chat] asking AI for identityKey={}", identityKey);
            ChatMessage typing = new ChatMessage();
            typing.setSender("AI");
            typing.setType("TYPING");
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", typing, createHeaders(sessionId));

            final String finalIdentityKey = identityKey; // for lambda
            CompletableFuture.runAsync(() -> {
                try {
                    if (isCarRelatedQuery(message.getContent()) && !isInformationQuery(message.getContent())) {
                        log.info("[AI] Processing recommendation query for identityKey={}", finalIdentityKey);
                        try {
                            var req = chatParser.parse(message.getContent(), 5);
                            var results = aiRecommendationService.recommend(req);

                            if (results != null && !results.isEmpty()) {
                                ChatMessage aiMsg = new ChatMessage();
                                aiMsg.setSender("AI");
                                aiMsg.setContent("Dựa trên yêu cầu của bạn, tôi gợi ý cho bạn một số mẫu xe sau:");
                                aiMsg.setType("CAR_LIST");
                                aiMsg.setData(results); 
                                aiMsg.setTimestamp(Instant.now().toString());
                                messagingTemplate.convertAndSendToUser(sessionId, "/queue/replies", aiMsg,
                                        createHeaders(sessionId));
                                        
                                // Update AI memory manually for recommendation hits
                                aiChatService.updateHistory(finalIdentityKey, message.getContent(), aiMsg.getContent());
                            } else {
                                String reply = aiChatService.getReply(message.getContent(), finalIdentityKey, message.getMetadata());
                                sendAiReply(sessionId, reply);
                            }
                        } catch (Exception e) {
                            log.error("[chat] Recommendation error: {}", e.getMessage());
                            String reply = aiChatService.getReply(message.getContent(), finalIdentityKey, message.getMetadata());
                            sendAiReply(sessionId, reply);
                        }
                    } else {
                        String reply = aiChatService.getReply(message.getContent(), finalIdentityKey, message.getMetadata());
                        sendAiReply(sessionId, reply);
                    }
                } catch (Exception e) {
                    log.error("[chat] Unexpected error for identityKey {}: {}", finalIdentityKey, e.getMessage(), e);
                    sendAiReply(sessionId, "Xin lỗi, đã có lỗi xảy ra. Hãy thử lại sau.");
                }
            });
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
        // Expand keywords for information-seeking queries (service procedures, policies, etc.)
        return lower.contains("thông tin") || lower.contains("đánh giá") ||
                lower.contains("ưu điểm") || lower.contains("nhược điểm") ||
                lower.contains("so sánh") || lower.contains("ý kiến") ||
                lower.contains("mô tả") || lower.contains("review") ||
                lower.contains("info") || lower.contains("thông số") ||
                lower.contains("kỹ thuật") || lower.contains("động cơ") ||
                lower.contains("màu sắc") || lower.contains("nội thất") ||
                lower.contains("ngoại thất") || lower.contains("tiêu thụ") ||
                lower.contains("hướng dẫn") || lower.contains("thủ tục") ||
                lower.contains("quy trình") || lower.contains("giấy tờ") ||
                lower.contains("bảo hiểm") || lower.contains("đặt cọc") ||
                lower.contains("cọc") || lower.contains("thanh toán") ||
                lower.contains("hủy") || lower.contains("bằng lái") ||
                lower.contains("đăng ký");
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
