package com.alertas.estructura.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grados")
@Getter
@Setter
public class Grado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gra_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "gra_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "gra_nombre", nullable = false, length = 30)
    private String nombre;

    // -2 prejardin, -1 jardin, 0 transicion, 1 a 11
    @Column(name = "gra_orden", nullable = false)
    private int orden;

    @Column(name = "gra_activo", nullable = false)
    private boolean activo = true;
}
