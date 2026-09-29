-- prueba de aislamiento y reglas del esquema. termina en rollback
\set ON_ERROR_STOP on
BEGIN;

-- datos de prueba como owner (no le aplica RLS)
SET LOCAL ROLE alertas_owner;

INSERT INTO instituciones (ins_id, ins_nombre, ins_slug) OVERRIDING SYSTEM VALUE VALUES
    (900001, 'Colegio A', 'colegio-a'),
    (900002, 'Colegio B', 'colegio-b');

INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE VALUES
    (910001, 900001, '1001', 'x', 'ESTUDIANTE'),
    (910002, 900002, '1001', 'x', 'ESTUDIANTE'),     -- mismo documento en otra institucion, se permite
    (910003, 900001, '2001', 'x', 'PSICORIENTADOR'),
    (910004, 900001, '3001', 'x', 'DOCENTE');

INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES
    (920001, 900001, 910001, 'estA', 'qrA', 'TI', '1001', 'José', 'Pérez'),
    (920002, 900002, 910002, 'estB', 'qrB', 'TI', '1001', 'José', 'Pérez');

INSERT INTO personal (per_id, per_ins_id, per_usu_id, per_codigo, per_tipo_doc, per_nro_doc, per_nombres, per_apellidos)
OVERRIDING SYSTEM VALUE VALUES (930001, 900001, 910003, 'perA', 'CC', '2001', 'Ana', 'Gomez');

INSERT INTO anios_lectivos (anl_id, anl_ins_id, anl_anio, anl_activo) OVERRIDING SYSTEM VALUE VALUES
    (940001, 900001, 2026, true),
    (940002, 900002, 2026, true);
INSERT INTO grados (gra_id, gra_ins_id, gra_nombre, gra_orden) OVERRIDING SYSTEM VALUE VALUES
    (950001, 900001, 'Sexto', 6),
    (950002, 900002, 'Sexto', 6);
INSERT INTO grupos (grp_id, grp_ins_id, grp_anl_id, grp_gra_id, grp_nombre) OVERRIDING SYSTEM VALUE VALUES
    (960001, 900001, 940001, 950001, 'A'),
    (960002, 900002, 940002, 950002, 'A');
INSERT INTO categorias_alerta (cat_id, cat_ins_id, cat_nombre) OVERRIDING SYSTEM VALUE VALUES
    (970001, 900001, 'Convivencia');

-- 1. restricciones
DO $$
BEGIN
    -- ubicar a un estudiante de A en un grupo de B
    BEGIN
        INSERT INTO matriculas (mat_ins_id, mat_est_id, mat_anl_id, mat_grp_id) VALUES (900001, 920001, 940001, 960002);
        RAISE EXCEPTION 'FALLO: se permitio ubicar en un grupo de otra institucion';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  no se ubica en un grupo de otra institucion';
    END;

    -- familiar marcado "recibe SMS" sin celular
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 1, 'Rosa', 'ABUELO');
        RAISE EXCEPTION 'FALLO: recibe SMS sin celular';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  recibe SMS exige celular';
    END;
END $$;

INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco, fam_celular)
VALUES (900001, 920001, 1, 'Maria', 'MADRE', '3001234567'),
       (900001, 920001, 2, 'Luis',  'PADRE', '3001234568'),
       (900001, 920001, 3, 'Rosa',  'ABUELO', '3001234569');

DO $$
BEGIN
    BEGIN
        INSERT INTO familiares (fam_ins_id, fam_est_id, fam_posicion, fam_nombres, fam_parentesco)
        VALUES (900001, 920001, 4, 'Pedro', 'TIO');
        RAISE EXCEPTION 'FALLO: se permitio un cuarto familiar';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  cuarto familiar rechazado';
    END;

    -- dos anios activos en la misma institucion
    BEGIN
        INSERT INTO anios_lectivos (anl_ins_id, anl_anio, anl_activo) VALUES (900001, 2027, true);
        RAISE EXCEPTION 'FALLO: se permitieron dos anios activos';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  un solo anio activo';
    END;

    -- documento repetido en la misma institucion (aunque cambie el tipo)
    BEGIN
        INSERT INTO usuarios (usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) VALUES (900001, '9999', 'x', 'ESTUDIANTE');
        INSERT INTO estudiantes (est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
        VALUES (900001, currval(pg_get_serial_sequence('usuarios', 'usu_id')), 'estX', 'qrX', 'RC', '1001', 'Otro', 'Nino');
        RAISE EXCEPTION 'FALLO: se permitio un documento repetido';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  documento unico por institucion';
    END;
END $$;

-- 2. aislamiento con alertas_app
SET LOCAL ROLE alertas_app;

-- sin tenant: no se ve nada
SELECT set_config('app.current_tenant_id', '', true);
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 0, 'FALLO: sin tenant se ven estudiantes';
    ASSERT (SELECT count(*) FROM usuarios) = 0,    'FALLO: sin tenant se ven usuarios';
    RAISE NOTICE 'OK  sin tenant no se ve nada';
END $$;

-- con el tenant A
SELECT set_config('app.current_tenant_id', '900001', true);
DO $$
DECLARE
    filas integer;
BEGIN
    ASSERT (SELECT count(*) FROM estudiantes) = 1,                          'FALLO: A deberia ver 1 estudiante';
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_id = 920002) = 0,    'FALLO: A ve un estudiante de B';
    ASSERT (SELECT count(*) FROM grupos) = 1,                               'FALLO: A ve grupos de B';
    RAISE NOTICE 'OK  A solo ve sus filas';

    -- modificar y borrar filas de B: no afecta nada
    UPDATE estudiantes SET est_nombres = 'Hackeado' WHERE est_id = 920002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A modifico un estudiante de B';

    DELETE FROM grupos WHERE grp_id = 960002;
    GET DIAGNOSTICS filas = ROW_COUNT;
    ASSERT filas = 0, 'FALLO: A borro un grupo de B';
    RAISE NOTICE 'OK  A no modifica ni borra filas de B';

    -- insertar una fila con el tenant de B
    BEGIN
        INSERT INTO categorias_alerta (cat_ins_id, cat_nombre) VALUES (900002, 'Intrusa');
        RAISE EXCEPTION 'FALLO: A inserto en B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no inserta con el tenant de B';
    END;

    -- mover una fila propia a B
    BEGIN
        UPDATE categorias_alerta SET cat_ins_id = 900002 WHERE cat_id = 970001;
        RAISE EXCEPTION 'FALLO: A movio una fila a B';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  A no mueve filas a B';
    END;

    -- la bitacora no se puede modificar
    BEGIN
        INSERT INTO bitacora (bit_ins_id, bit_usu_id, bit_accion, bit_entidad) VALUES (900001, 910003, 'PRUEBA', 'prueba');
        UPDATE bitacora SET bit_accion = 'ALTERADA';
        RAISE EXCEPTION 'FALLO: se modifico la bitacora';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  bitacora de solo insercion';
    END;
END $$;

-- 3. alertas y citas
INSERT INTO matriculas (mat_id, mat_ins_id, mat_est_id, mat_anl_id, mat_grp_id) OVERRIDING SYSTEM VALUE
VALUES (980001, 900001, 920001, 940001, 960001);

DO $$
BEGIN
    -- una matricula cerrada necesita fecha de cierre
    BEGIN
        UPDATE matriculas SET mat_estado = 'RETIRADA' WHERE mat_id = 980001;
        RAISE EXCEPTION 'FALLO: matricula cerrada sin fecha de cierre';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  matricula cerrada exige fecha';
    END;

    -- las matriculas no se borran
    BEGIN
        DELETE FROM matriculas WHERE mat_id = 980001;
        RAISE EXCEPTION 'FALLO: se borro una matricula';
    EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'OK  matriculas sin borrado';
    END;
END $$;

INSERT INTO alertas (ale_ins_id, ale_codigo, ale_est_id, ale_origen, ale_reportada_por, ale_cat_id, ale_nivel,
                     ale_descripcion, ale_anl_id, ale_grp_id, ale_mat_id)
VALUES (900001, 'aleA', 920001, 'DOCENTE', 910004, 970001, 'CRITICO', 'Descripcion de prueba de la alerta', 940001, 960001, 980001);

DO $$
BEGIN
    ASSERT (SELECT ale_prioritaria FROM alertas LIMIT 1), 'FALLO: una alerta critica deberia ser prioritaria';

    -- completada exige fecha de cierre
    BEGIN
        UPDATE alertas SET ale_estado = 'COMPLETADA';
        RAISE EXCEPTION 'FALLO: alerta completada sin fecha';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  completada exige fecha';
    END;

    -- campos de solicitud del estudiante en una alerta de docente
    BEGIN
        UPDATE alertas SET ale_horario_seguro = 'Manana';
        RAISE EXCEPTION 'FALLO: campos de estudiante en alerta de docente';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK  campos de solicitud solo para estudiantes';
    END;
END $$;

UPDATE alertas SET ale_estado = 'EN_PROCESO', ale_psi_id = 930001, ale_asignada_en = now();

INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por, cit_mat_id, cit_grp_id)
VALUES (900001, 'citA', 920001, 930001, '2026-10-01 10:00-05', '2026-10-01 10:45-05', 'PRESENCIAL', 910003, 980001, 960001);

INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
SELECT 900001, c.cit_id, a.ale_id, 920001 FROM citas c, alertas a;

DO $$
BEGIN
    -- segunda cita programada para el mismo estudiante
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por, cit_mat_id, cit_grp_id)
        VALUES (900001, 'citB', 920001, 930001, '2026-10-02 10:00-05', '2026-10-02 10:45-05', 'PRESENCIAL', 910003, 980001, 960001);
        RAISE EXCEPTION 'FALLO: dos citas programadas para el mismo estudiante';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK  una sola cita programada por estudiante';
    END;
END $$;

-- cerrar la cita y probar el cruce de horario con otro estudiante
UPDATE citas SET cit_estado = 'REALIZADA', cit_cerrada_en = now();
SET LOCAL ROLE alertas_owner;
INSERT INTO usuarios (usu_id, usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol) OVERRIDING SYSTEM VALUE
VALUES (910005, 900001, '1002', 'x', 'ESTUDIANTE');
INSERT INTO estudiantes (est_id, est_ins_id, est_usu_id, est_codigo, est_codigo_qr, est_tipo_doc, est_nro_doc, est_nombres, est_apellidos)
OVERRIDING SYSTEM VALUE VALUES (920003, 900001, 910005, 'estC', 'qrC', 'TI', '1002', 'Laura', 'Rios');
INSERT INTO matriculas (mat_id, mat_ins_id, mat_est_id, mat_anl_id, mat_grp_id) OVERRIDING SYSTEM VALUE
VALUES (980002, 900001, 920003, 940001, 960001);
SET LOCAL ROLE alertas_app;
SELECT set_config('app.current_tenant_id', '900001', true);

DO $$
BEGIN
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por, cit_mat_id, cit_grp_id)
        VALUES (900001, 'citC', 920003, 930001, '2026-10-01 10:30-05', '2026-10-01 11:15-05', 'VIRTUAL', 910003, 980002, 960001);
        RAISE EXCEPTION 'FALLO: se permitieron citas cruzadas del mismo psicorientador';
    EXCEPTION WHEN exclusion_violation THEN RAISE NOTICE 'OK  sin citas cruzadas';
    END;

    -- relacionar la cita de un estudiante con la alerta de otro
    BEGIN
        INSERT INTO citas (cit_ins_id, cit_codigo, cit_est_id, cit_psi_id, cit_inicio, cit_fin, cit_modalidad, cit_creada_por, cit_mat_id, cit_grp_id)
        VALUES (900001, 'citD', 920003, 930001, '2026-10-01 12:00-05', '2026-10-01 12:45-05', 'VIRTUAL', 910003, 980002, 960001);
        INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id)
        SELECT 900001, (SELECT max(cit_id) FROM citas), ale_id, 920003 FROM alertas;
        RAISE EXCEPTION 'FALLO: cita de un estudiante con alerta de otro';
    EXCEPTION WHEN foreign_key_violation THEN RAISE NOTICE 'OK  cita y alerta del mismo estudiante';
    END;

    -- busqueda sin tildes (el dato tiene tildes a proposito)
    ASSERT (SELECT count(*) FROM estudiantes WHERE est_busqueda LIKE '%' || lower(f_unaccent('JOSE PEREZ')) || '%') = 1,
        'FALLO: la busqueda sin tildes no encuentra a Jose Perez';
    RAISE NOTICE 'OK  busqueda sin tildes';
END $$;

-- 4. estadisticas del superadmin: sin tenant la app no ve filas, pero las funciones sa_* si dan los totales
SET LOCAL ROLE alertas_app;
SELECT set_config('app.current_tenant_id', '', true);
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM alertas) = 0, 'FALLO: sin tenant se ven alertas';
    ASSERT (SELECT alertas FROM sa_resumen(NULL, NULL, NULL, 'America/Bogota')) > 0,
        'FALLO: sa_resumen no ve las alertas';
    ASSERT (SELECT sum(total) FROM sa_usuarios_por_rol(NULL)) > 0, 'FALLO: sa_usuarios_por_rol no ve usuarios';
    RAISE NOTICE 'OK  el superadmin ve totales entre colegios sin ver filas';
END $$;

\echo '=== TODAS LAS PRUEBAS PASARON ==='
ROLLBACK;
