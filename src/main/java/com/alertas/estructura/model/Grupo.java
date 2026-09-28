package com.alertas.estructura.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// un grupo es de un anio y un grado: "6 A" de 2026 no es el mismo "6 A" de 2027
@Entity
@Table(name = "grupos")
@Getter
@Setter
public class Grupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grp_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "grp_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grp_anl_id", nullable = false, updatable = false)
    private AnioLectivo anio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grp_gra_id", nullable = false, updatable = false)
    private Grado grado;

    @Column(name = "grp_nombre", nullable = false, length = 20)
    private String nombre;

    @Column(name = "grp_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
