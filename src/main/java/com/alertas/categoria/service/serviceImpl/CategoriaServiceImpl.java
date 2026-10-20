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

    private final CategoriaAlertaRepository repository;
    private final EntityManager em;

    public CategoriaServiceImpl(CategoriaAlertaRepository repository, EntityManager em) {

        this.repository = repository;
        this.em = em;
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
        for (CategoriaAlerta categoria : repository.todas()) {
            long total = alertasPorCategoria.getOrDefault(categoria.getId(), 0L);
            respuesta.add(CategoriaResponse.desde(categoria, total));
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponse buscar(Long id) {

        TenantSupport.requireTenant(em);

        CategoriaAlerta categoria = repository.findById(id).orElse(null);

        if (categoria == null) {
            throw ApiException.noEncontrado("La categoria no existe");
        }

        return CategoriaResponse.desde(categoria, 0);
    }
}
