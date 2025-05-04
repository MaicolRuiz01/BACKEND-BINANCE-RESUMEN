package com.binance.web.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="futures")
public class Futures {


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String symbol;
    private double positionAmt;
    private double entryPrice;
    private double breakEvenPrice;
    private double markPrice;
    private double unRealizedProfit;
    private double liquidationPrice;
    private int leverage;
    private double maxNotionalValue;
    private String marginType;
    private double isolatedMargin;
    private boolean isAutoAddMargin;
    private String positionSide;
    private double notional;
    private double isolatedWallet;
    private long updateTime;
    private boolean isolated;
    private int adlQuantile;

}
