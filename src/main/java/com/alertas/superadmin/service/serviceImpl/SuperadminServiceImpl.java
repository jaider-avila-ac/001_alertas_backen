package com.alertas.superadmin.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.auth.service.JwtService;
import com.alertas.auth.service.LimiteIntentosService;
import com.alertas.auth.service.SesionService;
import com.alertas.shared.exception.ApiException;
import com.alertas.superadmin.dto.CambiarContrasenaRequest;
import com.alertas.superadmin.dto.LoginSuperadminRequest;
import com.alertas.superadmin.dto.LoginSuperadminResponse;
import com.alertas.superadmin.dto.SuperadminResponse;
import com.alertas.superadmin.model.Superadministrador;
import com.alertas.superadmin.repository.SuperadministradorRepository;
import com.alertas.superadmin.service.SuperadminService;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperadminServiceImpl implements SuperadminService {

    private static final Logger log = LoggerFactory.getLogger(SuperadminServiceImpl.class);

    private static final String CREDENCIALES_MALAS = "Usuario o contrasena incorrectos";

    private final SuperadministradorRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SesionService sesionService;
    private final LimiteIntentosService limiteIntentos;

    public SuperadminServiceImpl(
            SuperadministradorRepository repository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SesionService sesionService,
            LimiteIntentosService limiteIntentos) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sesionService = sesionService;
        this.limiteIntentos = limiteIntentos;
    }

    @Override
    @Transactional
    public LoginSuperadminResponse login(LoginSuperadminRequest request, String ip) {

        String usuario = request.usuario().trim();
        String claveUsuario = "sa:" + usuario;
        String claveIp = "ip:" + ip;

        if (limiteIntentos.estaBloqueado(claveUsuario, claveIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera " + limiteIntentos.getBloqueoMinutos() + " minutos");
        }

        Superadministrador superadmin = repository.findByUsuario(usuario).orElse(null);

        if (superadmin == null || !superadmin.isActivo()
                || !passwordEncoder.matches(request.contrasena(), superadmin.getContrasenaHash())) {
            limiteIntentos.registrarFallo(claveUsuario, claveIp);
            throw ApiException.noAutorizado(CREDENCIALES_MALAS);
        }

        limiteIntentos.limpiar(claveUsuario);
        superadmin.setUltimoIngreso(OffsetDateTime.now());

        String token = jwtService.generar(superadmin.getId(), null, null, Rol.SUPERADMIN);
        return new LoginSuperadminResponse(token, SuperadminResponse.desde(superadmin));
    }

    @Override
    @Transactional(readOnly = true)
    public SuperadminResponse buscar(Long id) {
        return SuperadminResponse.desde(obtener(id));
    }

    @Override
    @Transactional
    public LoginSuperadminResponse cambiarContrasena(Long id, CambiarContrasenaRequest request) {

        Superadministrador superadmin = obtener(id);

        if (!passwordEncoder.matches(request.actual(), superadmin.getContrasenaHash())) {
            throw ApiException.invalido("La contrasena actual no es correcta");
        }

        if (request.actual().equals(request.nueva())) {
            throw ApiException.invalido("La contrasena nueva debe ser diferente a la actual");
        }

        superadmin.setContrasenaHash(passwordEncoder.encode(request.nueva()));
        superadmin.setContrasenaCambiadaEn(OffsetDateTime.now());

        // se cierran las sesiones en otros equipos y se entrega un token nuevo para este
        sesionService.cerrarSesiones(null, id);
        String token = jwtService.generar(id, null, null, Rol.SUPERADMIN);

        return new LoginSuperadminResponse(token, SuperadminResponse.desde(superadmin));
    }

    @Override
    @Transactional
    public void crearInicialSiNoExiste(String usuario, String contrasena, String nombres) {

        if (repository.count() > 0) {
            return;
        }

        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.length() < 8) {
            log.warn("No hay superadmin. Define SUPERADMIN_USUARIO y SUPERADMIN_CONTRASENA (minimo 8) para crear el primero");
            return;
        }

        String nombreFinal = "Superadministrador";

        if (nombres != null && !nombres.isBlank()) {
            nombreFinal = nombres.trim();
        }

        Superadministrador superadmin = new Superadministrador();
        superadmin.setUsuario(usuario.trim());
        superadmin.setNombres(nombreFinal);
        superadmin.setContrasenaHash(passwordEncoder.encode(contrasena));
        superadmin.setActivo(true);

        repository.save(superadmin);
        log.info("Se creo el superadmin inicial '{}'", superadmin.getUsuario());
    }

    private Superadministrador obtener(Long id) {

        Superadministrador superadmin = repository.findById(id).orElse(null);

        if (superadmin == null || !superadmin.isActivo()) {
            throw ApiException.noEncontrado("El superadmin no existe");
        }

        return superadmin;
    }
}
