package com.binance.web.Entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="account_binance")
public class AccountBinance {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String name;
	private String referenceAccount;
	//sera en dolares el balance
	//private Double balance;
	private String correo;
	private String userBinance;
	//esto se usa para identificar a las cuentas en la parte de traspasos en billetra spot
	private String address;
	//esto es para diferencial si es de binance o truswallet
	private String tipo;
	
	private String apiKey;
	private String apiSecret;
	@OneToMany(mappedBy = "accountBinance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccountCryptoBalance> cryptoBalances = new ArrayList<>();
}
