package com.binance.web.auth;

import com.binance.web.Entity.Rol;

public record RegisterRequest(String username, String password, Rol rol) {}
