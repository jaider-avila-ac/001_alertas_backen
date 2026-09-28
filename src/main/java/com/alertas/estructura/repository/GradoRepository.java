package com.alertas.estructura.repository;

import com.alertas.estructura.model.Grado;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradoRepository extends JpaRepository<Grado, Long> {

    List<Grado> findAllByOrderByOrdenAsc();
}
