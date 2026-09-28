package com.alertas.estructura.model;

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

@Entity
@Table(name = "anios_lectivos")
@Getter
@Setter
public class AnioLectivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "anl_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "anl_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "anl_anio", nullable = false)
    private int anio;

    @Column(name = "anl_activo", nullable = false)
    private boolean activo;

    @Column(name = "anl_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
