package com.alertas.usuario.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.auth.service.SesionService;
import com.alertas.bitacora.service.BitacoraService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import com.alertas.usuario.model.Usuario;
import com.alertas.usuario.repository.UsuarioRepository;
import com.alertas.usuario.service.UsuarioService;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final String SOLO_SUPERADMIN =
            "La contrasena y el estado de un administrador solo los maneja el superadmin";

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SesionService sesionService;
    private final BitacoraService bitacoraService;
    private final EntityManager em;

    public UsuarioServiceImpl(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            SesionService sesionService,
            BitacoraService bitacoraService,
            EntityManager em) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sesionService = sesionService;
        this.bitacoraService = bitacoraService;
        this.em = em;
    }

    @Override
    @Transactional
    public Usuario crear(String documento, Rol rol) {

        Long institucionId = TenantSupport.requireTenant(em);

        if (rol == Rol.SUPERADMIN) {
            throw new IllegalArgumentException("El superadmin no es usuario de una institucion");
        }

        if (repository.existsByUsuario(documento)) {
            throw ApiException.conflicto("Ya existe un usuario con el documento " + documento);
        }

        Usuario usuario = new Usuario();
        usuario.setInstitucionId(institucionId);
        usuario.setUsuario(documento);
        usuario.setContrasenaHash(passwordEncoder.encode(documento));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setDebeCambiarContrasena(debeCambiarAlRestablecer(rol));

        return repository.save(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeDocumento(String documento) {

        TenantSupport.requireTenant(em);
        return repository.existsByUsuario(documento);
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long usuarioId) {

        TenantSupport.requireTenant(em);

        Usuario usuario = repository.findById(usuarioId).orElse(null);

        if (usuario == null) {
            throw ApiException.noEncontrado("El usuario no existe");
        }

        return usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario buscarPorDocumento(String documento) {

        TenantSupport.requireTenant(em);
        return repository.findByUsuario(documento).orElse(null);
    }

    @Override
    @Transactional
    public void registrarIngreso(Long usuarioId) {

        Usuario usuario = buscarPorId(usuarioId);
        usuario.setUltimoIngreso(OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void cambiarDocumento(Long usuarioId, String documento) {

        Usuario usuario = buscarPorId(usuarioId);

        if (usuario.getUsuario().equals(documento)) {
            return;
        }

        if (repository.existsByUsuario(documento)) {
            throw ApiException.conflicto("Ya existe un usuario con el documento " + documento);
        }

        usuario.setUsuario(documento);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> documentosEnUso(Collection<String> documentos) {

        TenantSupport.requireTenant(em);

        Set<String> enUso = new HashSet<>();

        if (documentos.isEmpty()) {
            return enUso;
        }

        enUso.addAll(repository.documentosExistentes(documentos));
        return enUso;
    }

    @Override
    @Transactional
    public Map<String, Usuario> crearVarios(List<String> documentos, Rol rol) {

        Long institucionId = TenantSupport.requireTenant(em);
        Map<String, Usuario> creados = new HashMap<>();

        if (documentos.isEmpty()) {
            return creados;
        }

        // bcrypt tarda ~80 ms por contrasena: 2000 estudiantes serian casi 3 minutos en fila.
        // se reparte entre los nucleos del servidor
        Map<String, String> hashes = cifrarEnParalelo(documentos);

        List<Usuario> nuevos = new ArrayList<>();

        for (String documento : documentos) {
            Usuario usuario = new Usuario();
            usuario.setInstitucionId(institucionId);
            usuario.setUsuario(documento);
            usuario.setContrasenaHash(hashes.get(documento));
            usuario.setRol(rol);
            usuario.setActivo(true);
            usuario.setDebeCambiarContrasena(debeCambiarAlRestablecer(rol));
            nuevos.add(usuario);
        }

        repository.saveAll(nuevos);

        for (Usuario usuario : nuevos) {
            creados.put(usuario.getUsuario(), usuario);
        }

        return creados;
    }

    private Map<String, String> cifrarEnParalelo(List<String> documentos) {

        int hilos = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        Map<String, Future<String>> pendientes = new HashMap<>();

        try {
            for (String documento : documentos) {
                Future<String> hash = ejecutor.submit(() -> passwordEncoder.encode(documento));
                pendientes.put(documento, hash);
            }

            Map<String, String> hashes = new HashMap<>();

            for (Map.Entry<String, Future<String>> entrada : pendientes.entrySet()) {
                hashes.put(entrada.getKey(), entrada.getValue().get());
            }

            return hashes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio el cifrado de contrasenas", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Fallo cifrando contrasenas", e);
        } finally {
            ejecutor.shutdown();
        }
    }

    // ---------------------------------------------------------------- contrasenas

    @Override
    @Transactional
    public void restablecerContrasena(Long usuarioId) {

        Long institucionId = TenantSupport.requireTenant(em);
        Usuario usuario = buscarPorId(usuarioId);

        usuario.setContrasenaHash(passwordEncoder.encode(usuario.getUsuario()));
        usuario.setDebeCambiarContrasena(debeCambiarAlRestablecer(usuario.getRol()));
        usuario.setContrasenaCambiadaEn(OffsetDateTime.now());

        // si alguien tenia la sesion abierta con la contrasena vieja, se cierra
        sesionService.cerrarSesiones(institucionId, usuarioId);
    }

    @Override
    @Transactional
    public void asignarContrasena(Long usuarioId, String nueva) {

        Long institucionId = TenantSupport.requireTenant(em);
        Usuario usuario = buscarPorId(usuarioId);

        usuario.setContrasenaHash(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarContrasena(false);
        usuario.setContrasenaCambiadaEn(OffsetDateTime.now());

        sesionService.cerrarSesiones(institucionId, usuarioId);
    }

    @Override
    @Transactional
    public void cambiarContrasenaPropia(Long usuarioId, String actual, String nueva) {

        Long institucionId = TenantSupport.requireTenant(em);
        Usuario usuario = buscarPorId(usuarioId);

        if (usuario.getRol() == Rol.ADMIN) {
            throw ApiException.prohibido("El administrador no puede cambiar su contrasena, pidesela al superadmin");
        }

        if (!passwordEncoder.matches(actual, usuario.getContrasenaHash())) {
            throw ApiException.invalido("La contrasena actual no es correcta");
        }

        if (actual.equals(nueva)) {
            throw ApiException.invalido("La contrasena nueva debe ser diferente a la actual");
        }

        if (nueva.equals(usuario.getUsuario())) {
            throw ApiException.invalido("La contrasena no puede ser tu numero de documento");
        }

        usuario.setContrasenaHash(passwordEncoder.encode(nueva));
        usuario.setDebeCambiarContrasena(false);
        usuario.setContrasenaCambiadaEn(OffsetDateTime.now());

        // se cierra en los otros equipos. el que la cambio recibe un token nuevo
        sesionService.cerrarSesiones(institucionId, usuarioId);
    }

    @Override
    @Transactional
    public Usuario restablecerContrasenaPorAdmin(Long usuarioId) {

        Usuario usuario = buscarPorId(usuarioId);

        if (usuario.getRol() == Rol.ADMIN) {
            throw ApiException.prohibido(SOLO_SUPERADMIN);
        }

        restablecerContrasena(usuarioId);
        bitacoraService.registrar("RESTABLECER_CONTRASENA", "usuario", usuarioId, null);

        return usuario;
    }

    // ---------------------------------------------------------------- activar e inactivar

    @Override
    @Transactional
    public void cambiarEstado(Long usuarioId, boolean activo) {

        Long institucionId = TenantSupport.requireTenant(em);
        Usuario usuario = buscarPorId(usuarioId);

        usuario.setActivo(activo);

        if (!activo) {
            sesionService.cerrarSesiones(institucionId, usuarioId);
        }
    }

    @Override
    @Transactional
    public Usuario cambiarEstadoPorAdmin(Long usuarioId, boolean activo) {

        Usuario usuario = buscarPorId(usuarioId);

        if (usuario.getRol() == Rol.ADMIN) {
            throw ApiException.prohibido(SOLO_SUPERADMIN);
        }

        cambiarEstado(usuarioId, activo);

        if (activo) {
            bitacoraService.registrar("ACTIVAR_USUARIO", "usuario", usuarioId, null);
        } else {
            bitacoraService.registrar("INACTIVAR_USUARIO", "usuario", usuarioId, null);
        }

        return usuario;
    }

    @Override
    @Transactional
    public int cambiarEstadoMasivo(Rol rol, List<Long> usuarioIds, boolean activo) {

        Long institucionId = TenantSupport.requireTenant(em);
        boolean porSeleccion = usuarioIds != null && !usuarioIds.isEmpty();

        if (!porSeleccion && (rol == null || rol == Rol.ADMIN || rol == Rol.SUPERADMIN)) {
            throw ApiException.invalido("Indica el rol (docente, psicorientador o estudiante) o selecciona usuarios");
        }

        int afectados;

        if (porSeleccion) {
            afectados = repository.cambiarEstadoPorIds(usuarioIds, activo);

            if (!activo) {
                for (Long usuarioId : usuarioIds) {
                    sesionService.cerrarSesiones(institucionId, usuarioId);
                }
            }
        } else {
            afectados = repository.cambiarEstadoPorRol(rol, activo);

            if (!activo) {
                sesionService.cerrarSesionesDelRol(institucionId, rol);
            }
        }

        String accion = "INACTIVAR_MASIVO";

        if (activo) {
            accion = "ACTIVAR_MASIVO";
        }

        String detalle = afectados + " usuarios";

        if (!porSeleccion) {
            detalle = detalle + " con rol " + rol.name();
        }

        bitacoraService.registrar(accion, "usuario", null, detalle);
        return afectados;
    }

    // el administrador no puede cambiar su contrasena, asi que no se le obliga
    private boolean debeCambiarAlRestablecer(Rol rol) {
        return rol != Rol.ADMIN;
    }
}
