package com.alertas.auth.service.serviceImpl;

import com.alertas.auth.config.JwtProperties;
import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {

    private final SecretKey llave;
    private final Duration duracion;

    public JwtServiceImpl(JwtProperties props) {

        this.llave = Keys.hmacShaKeyFor(props.secreto().getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(props.expiracionMinutos());
    }

    @Override
    public String generar(Long usuarioId, Long institucionId, String slug, Rol rol) {
        return generar(usuarioId, institucionId, slug, rol, false);
    }

    @Override
    public String generar(Long usuarioId, Long institucionId, String slug, Rol rol, boolean debeCambiarContrasena) {

        Instant ahora = Instant.now();

        JwtBuilder builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("rol", rol.name())
                // iat va en segundos, para cerrar sesiones hace falta el milisegundo
                .claim("emi", ahora.toEpochMilli())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)));

        if (debeCambiarContrasena) {
            builder.claim("cambiar", true);
        }

        if (institucionId != null) {
            builder.claim("ins", institucionId);
            builder.claim("slug", slug);
        }

        return builder.signWith(llave).compact();
    }

    @Override
    public UsuarioAutenticado leer(String token) {

        Claims claims;

        try {
            claims = Jwts.parser().verifyWith(llave).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }

        String rolTexto = claims.get("rol", String.class);
        Number emitido = claims.get("emi", Number.class);

        if (rolTexto == null || emitido == null) {
            return null;
        }

        Rol rol;

        try {
            rol = Rol.valueOf(rolTexto);
        } catch (IllegalArgumentException e) {
            return null;
        }

        Long institucionId = null;
        Number ins = claims.get("ins", Number.class);

        if (ins != null) {
            institucionId = ins.longValue();
        }

        boolean debeCambiar = Boolean.TRUE.equals(claims.get("cambiar", Boolean.class));

        return new UsuarioAutenticado(
                Long.valueOf(claims.getSubject()),
                institucionId,
                claims.get("slug", String.class),
                rol,
                debeCambiar,
                Instant.ofEpochMilli(emitido.longValue()));
    }

    @Override
    public Duration getDuracion() {
        return duracion;
    }
}
