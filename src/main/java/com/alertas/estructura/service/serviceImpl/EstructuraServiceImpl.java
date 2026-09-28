package com.alertas.estructura.service.serviceImpl;

import com.alertas.estructura.model.AnioLectivo;
import com.alertas.estructura.model.Grado;
import com.alertas.estructura.repository.AnioLectivoRepository;
import com.alertas.estructura.repository.GradoRepository;
import com.alertas.estructura.service.EstructuraService;
import com.alertas.shared.TenantSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstructuraServiceImpl implements EstructuraService {

    // la posicion en el arreglo es el orden + 2 (prejardin es -2)
    private static final String[] GRADOS = {
            "Prejardin", "Jardin", "Transicion",
            "Primero", "Segundo", "Tercero", "Cuarto", "Quinto",
            "Sexto", "Septimo", "Octavo", "Noveno", "Decimo", "Once"
    };

    private final GradoRepository gradoRepository;
    private final AnioLectivoRepository anioRepository;
    private final EntityManager em;

    public EstructuraServiceImpl(
            GradoRepository gradoRepository,
            AnioLectivoRepository anioRepository,
            EntityManager em) {

        this.gradoRepository = gradoRepository;
        this.anioRepository = anioRepository;
        this.em = em;
    }

    @Override
    @Transactional
    public void crearDatosIniciales() {

        Long institucionId = TenantSupport.requireTenant(em);

        List<Grado> grados = new ArrayList<>();

        for (int i = 0; i < GRADOS.length; i++) {
            Grado grado = new Grado();
            grado.setInstitucionId(institucionId);
            grado.setNombre(GRADOS[i]);
            grado.setOrden(i - 2);
            // prejardin y jardin casi ningun colegio publico los tiene, quedan apagados
            grado.setActivo(i >= 2);
            grados.add(grado);
        }

        gradoRepository.saveAll(grados);

        AnioLectivo anio = new AnioLectivo();
        anio.setInstitucionId(institucionId);
        anio.setAnio(LocalDate.now().getYear());
        anio.setActivo(true);

        anioRepository.save(anio);
    }
}
