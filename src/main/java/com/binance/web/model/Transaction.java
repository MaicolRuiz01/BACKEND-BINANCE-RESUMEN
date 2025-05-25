package com.binance.web.model;

import com.binance.web.config.CustomLocalDateTimeDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {

	private Long binanceId;
    private String transactionId;
    private Double amount;
    @JsonDeserialize(using = CustomLocalDateTimeDeserializer.class)
    private LocalDateTime transactionTime;

    
    @JsonProperty("payerInfo")
    private PayerInfo payerInfo;  // Clase interna para información del pagador

    @JsonProperty("receiverInfo")
    private ReceiverInfo receiverInfo;  // Clase interna para información del receptor

    // Getters y setters

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(LocalDateTime transactionTime) {
        this.transactionTime = transactionTime;
    }

    public PayerInfo getPayerInfo() {
        return payerInfo;
    }

    public void setPayerInfo(PayerInfo payerInfo) {
        this.payerInfo = payerInfo;
    }

    public ReceiverInfo getReceiverInfo() {
        return receiverInfo;
    }

    public void setReceiverInfo(ReceiverInfo receiverInfo) {
        this.receiverInfo = receiverInfo;
    }
    
    
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    // Clases internas para PayerInfo y ReceiverInfo
    public static class PayerInfo {
        private String name;
        private String email;
        private Long binanceId;

        // Getters y setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)

    public static class ReceiverInfo {
        private String name;
        private String email;
        private Long binanceId;
        // Getters y setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
        
        public Long getBinanceId() {  // <-- Getter nuevo!
            return binanceId;
        }
        public void setBinanceId(Long binanceId) { // <-- Setter nuevo!
            this.binanceId = binanceId;
        }
    }
}
