package com.alertas.superadmin.repository;

import com.alertas.superadmin.model.Superadministrador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuperadministradorRepository extends JpaRepository<Superadministrador, Long> {

    Optional<Superadministrador> findByUsuario(String usuario);
}
