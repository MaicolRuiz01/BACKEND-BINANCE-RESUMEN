package com.binance.web.futures;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

public class Futures {


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

}
