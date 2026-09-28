package com.alertas.superadmin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// no pertenece a ninguna institucion, esta tabla no tiene RLS
@Entity
@Table(name = "superadministradores")
@Getter
@Setter
public class Superadministrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sad_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "sad_usuario", nullable = false, length = 30)
    private String usuario;

    @Column(name = "sad_nombres", nullable = false, length = 120)
    private String nombres;

    @Column(name = "sad_contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Column(name = "sad_activo", nullable = false)
    private boolean activo = true;

    @Column(name = "sad_contrasena_cambiada_en")
    private OffsetDateTime contrasenaCambiadaEn;

    @Column(name = "sad_ultimo_ingreso")
    private OffsetDateTime ultimoIngreso;

    @Column(name = "sad_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
