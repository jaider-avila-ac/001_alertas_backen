package com.alertas.institucion.repository;

import com.alertas.institucion.model.Institucion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitucionRepository extends JpaRepository<Institucion, Long> {

    Optional<Institucion> findBySlug(String slug);
}
