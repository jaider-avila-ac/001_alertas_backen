package com.alertas.bitacora.repository;

import com.alertas.bitacora.model.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BitacoraRepository extends JpaRepository<Bitacora, Long> {
}
