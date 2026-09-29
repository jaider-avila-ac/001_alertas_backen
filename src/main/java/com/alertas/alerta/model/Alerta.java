package com.alertas.alerta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// una alerta tiene su propio estado. la del estudiante (solicitud de ayuda) es la misma entidad con origen ESTUDIANTE.
// estudiante, categoria, matricula y demas van como id para no amarrar este modulo a otros
@Entity
@Table(name = "alertas")
@Getter
@Setter
public class Alerta {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String COMPLETADA = "COMPLETADA";

    public static final String DOCENTE = "DOCENTE";
    public static final String ESTUDIANTE = "ESTUDIANTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ale_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "ale_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "ale_codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    @Column(name = "ale_est_id", nullable = false, updatable = false)
    private Long estudianteId;

    @Column(name = "ale_origen", nullable = false, updatable = false, length = 10)
    private String origen;

    // usuario que la creo
    @Column(name = "ale_reportada_por", nullable = false, updatable = false)
    private Long reportadaPor;

    @Column(name = "ale_cat_id", nullable = false)
    private Long categoriaId;

    @Column(name = "ale_nivel", nullable = false, length = 10)
    private String nivel;

    @Column(name = "ale_descripcion", nullable = false)
    private String descripcion;

    @Column(name = "ale_fecha_hecho")
    private LocalDate fechaHecho;

    @Column(name = "ale_lugar", length = 150)
    private String lugar;

    @Column(name = "ale_peligro_inmediato", nullable = false)
    private boolean peligroInmediato;

    // solo en solicitudes del estudiante
    @Column(name = "ale_horario_seguro", length = 150)
    private String horarioSeguro;

    @Column(name = "ale_modalidad_preferida", length = 10)
    private String modalidadPreferida;

    @Column(name = "ale_autoriza_sms_familiares")
    private Boolean autorizaSmsFamiliares;

    // anio, grupo y matricula del momento: las estadisticas no cambian si despues pasa de grado
    @Column(name = "ale_anl_id", nullable = false, updatable = false)
    private Long anioId;

    @Column(name = "ale_grp_id", nullable = false, updatable = false)
    private Long grupoId;

    @Column(name = "ale_mat_id", nullable = false, updatable = false)
    private Long matriculaId;

    @Column(name = "ale_estado", nullable = false, length = 12)
    private String estado = PENDIENTE;

    // psicorientador (personal)
    @Column(name = "ale_psi_id")
    private Long psicorientadorId;

    @Column(name = "ale_asignada_en")
    private OffsetDateTime asignadaEn;

    @Column(name = "ale_conclusion")
    private String conclusion;

    @Column(name = "ale_completada_en")
    private OffsetDateTime completadaEn;

    // la calcula la base de datos
    @Column(name = "ale_prioritaria", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Boolean prioritaria;

    @Version
    @Column(name = "ale_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private int version;

    @Column(name = "ale_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "ale_actualizado_en", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void alCrear() {

        creadoEn = OffsetDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
