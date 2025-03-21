package com.binance.web.BinanceAPI;

import java.util.Date;

import com.binance.web.OrderP2P.OrderP2PDto;
import com.google.gson.JsonObject;

public class OrderMapperServiceImpl {
    public static OrderP2PDto convertToDTO(JsonObject json) {
    	OrderP2PDto dto = new OrderP2PDto();
        
        dto.setOrderNumber(getJsonValue(json, "orderNumber"));
        dto.setTradeType(getJsonValue(json, "tradeType"));
        dto.setAmount(getJsonValue(json, "amount"));
        dto.setTotalPrice(getJsonValue(json, "totalPrice"));
        dto.setUnitPrice(getJsonValue(json, "unitPrice"));
        dto.setOrderStatus(getJsonValue(json, "orderStatus"));
        dto.setCreateTime(getJsonDateValue(json, "createTime"));
        dto.setCommission(getJsonValue(json, "commission"));
        dto.setCounterPartNickName(getJsonValue(json, "counterPartNickName"));
        dto.setPayMethodName(getJsonValue(json, "payMethodName"));

        return dto;
    }

    private static String getJsonValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
    
    private static Date getJsonDateValue(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            long timestamp = json.get(key).getAsLong();
            return new Date(timestamp); // Convierte el timestamp a Date
        }
        return null;
    }
}
