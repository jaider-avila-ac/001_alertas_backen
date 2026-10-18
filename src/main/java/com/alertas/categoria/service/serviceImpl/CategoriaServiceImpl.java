package com.alertas.categoria.service.serviceImpl;

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

    // son las mismas para todas las instituciones y siempre estan activas: nadie las crea, cambia ni apaga
    private static final String[] PREDETERMINADAS = {
            "Salud mental y emocional",
            "Convivencia escolar",
            "Violencia y vulneracion de derechos",
            "Asistencia y permanencia escolar",
            "Consumo de sustancias psicoactivas",
            "Dificultades academicas y de aprendizaje",
            "Salud y bienestar fisico",
            "Riesgos de seguridad escolar",
            "Proteccion e inclusion",
            "Situaciones familiares y sociales"
    };

    private final CategoriaAlertaRepository repository;
    private final EntityManager em;

    public CategoriaServiceImpl(CategoriaAlertaRepository repository, EntityManager em) {

        this.repository = repository;
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
    public List<CategoriaResponse> listar() {

        TenantSupport.requireTenant(em);

        // una sola consulta para los totales
        Map<Long, Long> alertasPorCategoria = new HashMap<>();
        for (Object[] fila : repository.contarAlertasPorCategoria()) {
            alertasPorCategoria.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        List<CategoriaResponse> respuesta = new ArrayList<>();
        for (CategoriaAlerta categoria : repository.activas()) {
            long total = alertasPorCategoria.getOrDefault(categoria.getId(), 0L);
            respuesta.add(CategoriaResponse.desde(categoria, total));
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponse buscarActiva(Long id) {

        TenantSupport.requireTenant(em);

        // RLS: una categoria de otro colegio no aparece
        CategoriaAlerta categoria = repository.findById(id).orElse(null);

        if (categoria == null) {
            throw ApiException.noEncontrado("La categoria no existe");
        }

        // las viejas que quedaron solo por el historial de sus alertas
        if (!categoria.isActiva()) {
            throw ApiException.invalido("La categoria " + categoria.getNombre() + " ya no se usa");
        }

        return CategoriaResponse.desde(categoria, 0);
    }
}
