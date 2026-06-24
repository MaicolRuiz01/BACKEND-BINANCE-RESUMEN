package com.binance.web.auth;

import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /** Login — público */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Credenciales incorrectas"));
        }

        Usuario user = usuarioRepository.findByUsername(request.username()).orElseThrow();
        String token = jwtService.generateToken(user, Map.of("rol", user.getRol().name()));

        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRol().name()));
    }

    /** Registrar nuevo usuario — solo ADMIN */
    @PostMapping("/registro")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> registro(@RequestBody RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El usuario ya existe"));
        }
        Usuario nuevo = Usuario.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .rol(request.rol())
                .build();
        usuarioRepository.save(nuevo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Usuario creado correctamente"));
    }

    /** Listar usuarios — solo ADMIN */
    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listarUsuarios() {
        List<Map<String, Object>> usuarios = usuarioRepository.findAll().stream()
                .map(u -> Map.<String, Object>of(
                        "id", u.getId(),
                        "username", u.getUsername(),
                        "rol", u.getRol().name()))
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    /** Cambiar contraseña de un usuario — solo ADMIN */
    @PutMapping("/usuarios/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cambiarPassword(@PathVariable Integer id,
                                              @RequestBody Map<String, String> body) {
        return usuarioRepository.findById(id).map(u -> {
            u.setPassword(passwordEncoder.encode(body.get("password")));
            usuarioRepository.save(u);
            return ResponseEntity.<Object>ok(Map.of("message", "Contraseña actualizada"));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Eliminar usuario — solo ADMIN */
    @DeleteMapping("/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Integer id) {
        if (!usuarioRepository.existsById(id)) return ResponseEntity.notFound().build();
        usuarioRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
