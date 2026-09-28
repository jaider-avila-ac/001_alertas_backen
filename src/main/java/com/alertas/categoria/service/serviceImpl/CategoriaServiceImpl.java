package com.alertas.categoria.service.serviceImpl;

import com.alertas.categoria.model.CategoriaAlerta;
import com.alertas.categoria.repository.CategoriaAlertaRepository;
import com.alertas.categoria.service.CategoriaService;
import com.alertas.shared.TenantSupport;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
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
}
