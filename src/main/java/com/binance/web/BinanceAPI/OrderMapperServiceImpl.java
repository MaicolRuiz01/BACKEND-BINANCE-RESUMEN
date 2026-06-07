package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.binance.web.OrderP2P.OrderP2PDto;
import com.fasterxml.jackson.databind.JsonNode;

public class OrderMapperServiceImpl {

    public static OrderP2PDto convertToDTO(JsonNode json) {
        OrderP2PDto dto = new OrderP2PDto();
        dto.setOrderNumber(textOrNull(json, "orderNumber"));
        dto.setTradeType(textOrNull(json, "tradeType"));
        dto.setAmount(doubleOrNull(json, "amount"));
        dto.setTotalPrice(doubleOrNull(json, "totalPrice"));
        dto.setUnitPrice(doubleOrNull(json, "unitPrice"));
        dto.setOrderStatus(textOrNull(json, "orderStatus"));
        dto.setCreateTime(dateOrNull(json, "createTime"));
        dto.setCommission(doubleOrNull(json, "commission"));
        dto.setCounterPartNickName(textOrNull(json, "counterPartNickName"));
        dto.setPayMethodName(textOrNull(json, "payMethodName"));
        return dto;
    }

    private static String textOrNull(JsonNode json, String key) {
        JsonNode node = json.get(key);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    private static LocalDateTime dateOrNull(JsonNode json, String key) {
        JsonNode node = json.get(key);
        if (node == null || node.isNull()) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.systemDefault());
    }

    private static Double doubleOrNull(JsonNode json, String key) {
        try {
            JsonNode node = json.get(key);
            return (node != null && !node.isNull()) ? node.asDouble() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
