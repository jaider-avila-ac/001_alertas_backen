package com.alertas.matricula.repository;

import com.alertas.matricula.model.MatriculaMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepository extends JpaRepository<MatriculaMovimiento, Long> {
}
