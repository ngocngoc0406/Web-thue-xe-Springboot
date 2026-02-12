package com.project.CarRental2.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.project.CarRental2.api.dto.RecommendationRequest;

@Component
public class ChatParser {

    // Extract seats, driver, features, price range, date range, address from free
    // text
    private static final Pattern SEATS_PATTERN = Pattern.compile("(\\d{1,2})\\s*(chỗ|chỗ ngồi|chỗ ng)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DRIVER_YES_PATTERN = Pattern.compile("(có tài|cần tài|có tài xế|có lái|cần lái)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DRIVER_NO_PATTERN = Pattern.compile("(tự lái|không cần tài|không tài|không lái)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile(
            "(?:từ|giá|giữa)\\s*([\\d.,]+)\\s*(k|nghìn|triệu|m|)\\s*(?:đến|-|to)?\\s*([\\d.,]+)?\\s*(k|nghìn|triệu|m|)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4})\\s*(?:đến|to|-)\\s*(\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_SINGLE_PATTERN = Pattern.compile("(\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("ở\\s+([^,\\.\\n]+)", Pattern.CASE_INSENSITIVE);

    public RecommendationRequest parse(String message, Integer topK) {
        RecommendationRequest req = new RecommendationRequest();
        if (message == null || message.isBlank()) {
            return req;
        }
        String m = message.trim();
        req.setText(m);
        if (topK != null)
            req.setTopK(topK);

        // seats
        Matcher seats = SEATS_PATTERN.matcher(m);
        if (seats.find()) {
            try {
                int s = Integer.parseInt(seats.group(1));
                req.setMinSeats(s);
            } catch (NumberFormatException e) {
            }
        }

        // driver
        if (DRIVER_YES_PATTERN.matcher(m).find()) {
            req.setDriver(true);
        } else if (DRIVER_NO_PATTERN.matcher(m).find()) {
            req.setDriver(false);
        }

        // features
        List<String> features = new ArrayList<>();
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("gps") || lower.contains("định vị") || lower.contains("gps locator"))
            features.add("gps");
        if (lower.contains("ghế trẻ em") || lower.contains("babyseat") || lower.contains("baby seat"))
            features.add("babyseat");
        if (lower.contains("cửa sổ trời") || lower.contains("sunroof"))
            features.add("sunroof");
        if (lower.contains("dvd"))
            features.add("dvd");
        if (lower.contains("bluetooth"))
            features.add("bluetooth");
        if (lower.contains("camera 360") || lower.contains("360"))
            features.add("camera360");
        if (lower.contains("camera lùi") || lower.contains("lùi") || lower.contains("reverse camera"))
            features.add("reversecamera");
        if (lower.contains("dash") || lower.contains("hành trình") || lower.contains("dashcam"))
            features.add("dashcamera");
        if (lower.contains("airbag") || lower.contains("túi khí"))
            features.add("airbags");
        if (lower.contains("usb"))
            features.add("usb");
        if (!features.isEmpty())
            req.setRequiredFeatures(features);

        // price
        Matcher price = PRICE_RANGE_PATTERN.matcher(m.replaceAll("ở", " "));
        if (price.find()) {
            String a = price.group(1);
            String unitA = price.group(2);
            String b = price.group(3);
            String unitB = price.group(4);
            Integer min = convertPriceToVnd(a, unitA);
            Integer max = b != null ? convertPriceToVnd(b, unitB) : null;
            if (min != null)
                req.setMinPrice(min);
            if (max != null)
                req.setMaxPrice(max);
        }

        // date range
        Matcher dr = DATE_RANGE_PATTERN.matcher(m);
        if (dr.find()) {
            String d1 = dr.group(1);
            String d2 = dr.group(2);
            String s1 = normalizeDate(d1);
            String s2 = normalizeDate(d2);
            if (s1 != null && s2 != null) {
                req.setDateStart(s1);
                req.setDateEnd(s2);
            }
        } else {
            // try single date (assume one-day trip)
            Matcher ds = DATE_SINGLE_PATTERN.matcher(m);
            if (ds.find()) {
                String d = ds.group(1);
                String s = normalizeDate(d);
                if (s != null) {
                    req.setDateStart(s);
                    req.setDateEnd(s);
                }
            }
        }

        // address
        Matcher addr = ADDRESS_PATTERN.matcher(m);
        if (addr.find()) {
            String a = addr.group(1).trim();
            if (a.length() > 0) {
                // cut if contains 'với' or 'từ' or 'trong'
                a = a.replaceAll("(với|từ|trong).*", "").trim();
                req.setAddress(a);
            }
        }

        return req;
    }

    private Integer convertPriceToVnd(String number, String unit) {
        if (number == null || number.isBlank())
            return null;
        try {
            String clean = number.replaceAll("[,\\.\\s]", "");
            double val = Double.parseDouble(clean);
            if (unit == null)
                unit = "";
            unit = unit.toLowerCase(Locale.ROOT);
            if (unit.contains("k") || unit.contains("nghìn")) {
                return (int) (val * 1000);
            } else if (unit.contains("triệu") || unit.contains("m")) {
                return (int) (val * 1_000_000);
            } else {
                // if number large, assume VND else assume thousands
                if (val > 1000)
                    return (int) val; // already in VND
                return (int) (val * 1000);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeDate(String raw) {
        if (raw == null)
            return null;
        raw = raw.replaceAll("\\s+", "");
        String[] patterns = { "d/M/yyyy", "d-M-yyyy", "d/M/yy", "d-M-yy", "yyyy-M-d", "yyyy/M/d" };
        for (String p : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(p);
                LocalDate dt = LocalDate.parse(raw, fmt);
                return dt.toString();
            } catch (DateTimeParseException e) {
                // try next
            }
        }
        // try ISO
        try {
            LocalDate dt = LocalDate.parse(raw);
            return dt.toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
