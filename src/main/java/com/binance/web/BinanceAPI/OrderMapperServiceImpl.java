package com.binance.web.BinanceAPI;

import java.time.*;
import java.time.LocalDateTime;

import com.binance.web.OrderP2P.OrderP2PDto;
import com.google.gson.JsonObject;

public class OrderMapperServiceImpl {
    public static OrderP2PDto convertToDTO(JsonObject json) {
    	OrderP2PDto dto = new OrderP2PDto();
        
        dto.setOrderNumber(getJsonValue(json, "orderNumber"));
        dto.setTradeType(getJsonValue(json, "tradeType"));
        dto.setAmount(getJsonDoubleValue(json, "amount"));
        dto.setTotalPrice(getJsonDoubleValue(json, "totalPrice"));
        dto.setUnitPrice(getJsonDoubleValue(json, "unitPrice"));
        dto.setOrderStatus(getJsonValue(json, "orderStatus"));
        dto.setCreateTime(getJsonDateValue(json, "createTime"));
        dto.setCommission(getJsonDoubleValue(json, "commission"));
        dto.setCounterPartNickName(getJsonValue(json, "counterPartNickName"));
        dto.setPayMethodName(getJsonValue(json, "payMethodName"));

        return dto;
    }

    private static String getJsonValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
    
    private static LocalDateTime getJsonDateValue(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            long timestamp = json.get(key).getAsLong();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        }
        return null;
    }
    
    private static Double getJsonDoubleValue(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsDouble()
                : null;
        } catch (Exception e) {
            return null;
        }
    }
}
