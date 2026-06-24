package com.binance.web.auth;

import com.binance.web.Entity.Rol;
import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea los usuarios por defecto al iniciar la app si no existen.
 * Usuarios iniciales:
 *   admin    / admin123    → ADMIN
 *   operario / operario123 → OPERARIO
 *
 * Cambia las contraseñas desde el panel de admin una vez en producción.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        crearSiNoExiste("admin",    "admin123",    Rol.ADMIN);
        crearSiNoExiste("operario", "operario123", Rol.OPERARIO);
    }

    private void crearSiNoExiste(String username, String password, Rol rol) {
        if (!usuarioRepository.existsByUsername(username)) {
            usuarioRepository.save(Usuario.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .rol(rol)
                    .build());
            log.info("[Auth] Usuario '{}' creado con rol {}", username, rol);
        }
    }
}
