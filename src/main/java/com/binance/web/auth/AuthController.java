package com.binance.web.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
	
	@PostMapping(value = "login")
	public String login() {
		return "Endpoint publico";
	}
	@PostMapping(value = "registro")
	public String registro() {
		return "registro exitoso";
	}
}
