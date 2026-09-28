package com.alertas.matricula.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// el estudiante en un anio: grupo, estado y como llego. una por anio, no se borra, se cierra.
// el anio y el grupo van como id para no amarrar este modulo a las entidades de estructura
@Entity
@Table(name = "matriculas")
@Getter
@Setter
public class Matricula {

    public static final String ACTIVA = "ACTIVA";
    public static final String PROMOVIDA = "PROMOVIDA";
    public static final String REPROBADA = "REPROBADA";
    public static final String GRADUADA = "GRADUADA";
    public static final String RETIRADA = "RETIRADA";

    public static final String NUEVA = "NUEVA";
    public static final String PROMOCION = "PROMOCION";
    public static final String REPITE = "REPITE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mat_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "mat_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "mat_est_id", nullable = false, updatable = false)
    private Long estudianteId;

    @Column(name = "mat_anl_id", nullable = false, updatable = false)
    private Long anioId;

    @Column(name = "mat_grp_id", nullable = false)
    private Long grupoId;

    @Column(name = "mat_estado", nullable = false, length = 10)
    private String estado = ACTIVA;

    @Column(name = "mat_origen", nullable = false, length = 10)
    private String origen = NUEVA;

    @Column(name = "mat_fecha_matricula", nullable = false)
    private LocalDate fechaMatricula;

    @Column(name = "mat_fecha_cierre")
    private LocalDate fechaCierre;

    @Column(name = "mat_motivo_cierre", length = 300)
    private String motivoCierre;

    @Column(name = "mat_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "mat_actualizado_en", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime actualizadoEn;

    public boolean isActiva() {
        return ACTIVA.equals(estado);
    }

    // estado final del anio. la fecha de cierre es hoy
    public void cerrar(String estadoFinal, String motivo) {

        estado = estadoFinal;
        fechaCierre = LocalDate.now();
        motivoCierre = motivo;
    }

    public void reabrir() {

        estado = ACTIVA;
        fechaCierre = null;
        motivoCierre = null;
    }

    @PrePersist
    void alCrear() {

        creadoEn = OffsetDateTime.now();
        actualizadoEn = creadoEn;

        if (fechaMatricula == null) {
            fechaMatricula = LocalDate.now();
        }
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
