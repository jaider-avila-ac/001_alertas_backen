package com.alertas.categoria.service.serviceImpl;

import com.alertas.bitacora.service.BitacoraService;
import com.alertas.categoria.dto.CategoriaResponse;
import com.alertas.categoria.model.CategoriaAlerta;
import com.alertas.categoria.repository.CategoriaAlertaRepository;
import com.alertas.categoria.service.CategoriaService;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaServiceImpl implements CategoriaService {

    private static final String[] PREDETERMINADAS = {
            "Convivencia y acoso escolar",
            "Consumo de sustancias",
            "Violencia intrafamiliar",
            "Salud mental",
            "Ausentismo escolar",
            "Otra"
    };

    private final CategoriaAlertaRepository repository;
    private final BitacoraService bitacoraService;
    private final EntityManager em;

    public CategoriaServiceImpl(CategoriaAlertaRepository repository, BitacoraService bitacoraService, EntityManager em) {

        this.repository = repository;
        this.bitacoraService = bitacoraService;
        this.em = em;
    }

    @Override
    @Transactional
    public void crearPredeterminadas() {

        Long institucionId = TenantSupport.requireTenant(em);

        List<CategoriaAlerta> categorias = new ArrayList<>();

        for (String nombre : PREDETERMINADAS) {
            CategoriaAlerta categoria = new CategoriaAlerta();
            categoria.setInstitucionId(institucionId);
            categoria.setNombre(nombre);
            categoria.setActiva(true);
            categorias.add(categoria);
        }

        repository.saveAll(categorias);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(boolean soloActivas) {

        TenantSupport.requireTenant(em);

        List<CategoriaAlerta> categorias;
        if (soloActivas) {
            categorias = repository.activas();
        } else {
            categorias = repository.todas();
        }

        // una sola consulta para los totales
        Map<Long, Long> alertasPorCategoria = new HashMap<>();
        for (Object[] fila : repository.contarAlertasPorCategoria()) {
            alertasPorCategoria.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        List<CategoriaResponse> respuesta = new ArrayList<>();
        for (CategoriaAlerta categoria : categorias) {
            long total = alertasPorCategoria.getOrDefault(categoria.getId(), 0L);
            respuesta.add(CategoriaResponse.desde(categoria, total));
        }

        return respuesta;
    }

    @Override
    @Transactional
    public CategoriaResponse crear(String nombre) {

        Long institucionId = TenantSupport.requireTenant(em);
        String limpio = limpiar(nombre);

        validarNombreLibre(limpio, 0L);

        CategoriaAlerta categoria = new CategoriaAlerta();
        categoria.setInstitucionId(institucionId);
        categoria.setNombre(limpio);
        categoria.setActiva(true);
        repository.save(categoria);

        bitacoraService.registrar("CREAR_CATEGORIA", "categoria_alerta", categoria.getId(), limpio);
        return CategoriaResponse.desde(categoria, 0);
    }

    @Override
    @Transactional
    public CategoriaResponse renombrar(Long id, String nombre) {

        TenantSupport.requireTenant(em);

        CategoriaAlerta categoria = obtener(id);
        String limpio = limpiar(nombre);

        validarNombreLibre(limpio, id);

        String anterior = categoria.getNombre();
        categoria.setNombre(limpio);

        // las alertas guardan el id, asi que las que ya existen quedan con el nombre nuevo
        bitacoraService.registrar("RENOMBRAR_CATEGORIA", "categoria_alerta", id, anterior + " -> " + limpio);
        return CategoriaResponse.desde(categoria, repository.contarAlertas(id));
    }

    @Override
    @Transactional
    public CategoriaResponse cambiarEstado(Long id, boolean activa) {

        TenantSupport.requireTenant(em);

        CategoriaAlerta categoria = obtener(id);

        if (!activa && categoria.isActiva() && repository.countByActivaTrue() <= 1) {
            throw ApiException.conflicto("Debe quedar al menos una categoria activa para poder crear alertas");
        }

        categoria.setActiva(activa);

        if (activa) {
            bitacoraService.registrar("ACTIVAR_CATEGORIA", "categoria_alerta", id, categoria.getNombre());
        } else {
            bitacoraService.registrar("DESACTIVAR_CATEGORIA", "categoria_alerta", id, categoria.getNombre());
        }

        return CategoriaResponse.desde(categoria, repository.contarAlertas(id));
    }

    @Override
    @Transactional
    public void borrar(Long id) {

        TenantSupport.requireTenant(em);

        CategoriaAlerta categoria = obtener(id);
        long alertas = repository.contarAlertas(id);

        if (alertas > 0) {
            throw ApiException.conflicto("La categoria tiene " + alertas + " alertas, no se puede borrar. Desactivala");
        }

        if (categoria.isActiva() && repository.countByActivaTrue() <= 1) {
            throw ApiException.conflicto("Debe quedar al menos una categoria activa para poder crear alertas");
        }

        repository.delete(categoria);
        bitacoraService.registrar("BORRAR_CATEGORIA", "categoria_alerta", id, categoria.getNombre());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponse buscarActiva(Long id) {

        TenantSupport.requireTenant(em);

        CategoriaAlerta categoria = obtener(id);

        if (!categoria.isActiva()) {
            throw ApiException.invalido("La categoria " + categoria.getNombre() + " esta desactivada");
        }

        return CategoriaResponse.desde(categoria, 0);
    }

    // ---------------------------------------------------------------- ayudas

    // RLS: una categoria de otro colegio no aparece
    private CategoriaAlerta obtener(Long id) {

        CategoriaAlerta categoria = repository.findById(id).orElse(null);

        if (categoria == null) {
            throw ApiException.noEncontrado("La categoria no existe");
        }

        return categoria;
    }

    private void validarNombreLibre(String nombre, Long sinContar) {

        if (repository.contarConNombre(nombre, sinContar) > 0) {
            throw ApiException.conflicto("Ya existe la categoria " + nombre);
        }
    }

    // sin espacios de sobra
    private String limpiar(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }
}
