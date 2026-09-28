-- RLS y permisos de alertas_app
--
-- alertas_app no es duenio de las tablas, por eso le aplica RLS.
-- sin FORCE para que las funciones SECURITY DEFINER de estadisticas del superadmin puedan ver todo
--

DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT * FROM (VALUES
            ('usuarios',          'usu_ins_id'),
            ('personal',          'per_ins_id'),
            ('estudiantes',       'est_ins_id'),
            ('familiares',        'fam_ins_id'),
            ('anios_lectivos',    'anl_ins_id'),
            ('grados',            'gra_ins_id'),
            ('grupos',            'grp_ins_id'),
            ('ubicaciones',       'ubi_ins_id'),
            ('categorias_alerta', 'cat_ins_id'),
            ('alertas',           'ale_ins_id'),
            ('citas',             'cit_ins_id'),
            ('citas_alertas',     'cia_ins_id'),
            ('notificaciones',    'not_ins_id'),
            ('sms_envios',        'sms_ins_id'),
            ('bitacora',          'bit_ins_id')
        ) AS v(tabla, columna)
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t.tabla);
        EXECUTE format(
            'CREATE POLICY p_%s_tenant ON %I USING (%I = tenant_actual()) WITH CHECK (%I = tenant_actual())',
            t.tabla, t.tabla, t.columna, t.columna);
    END LOOP;
END $$;


-- delete solo donde de verdad se borra, lo demas se inactiva
GRANT SELECT, INSERT, UPDATE ON instituciones, superadministradores TO alertas_app;

GRANT SELECT, INSERT, UPDATE ON
    usuarios, personal, estudiantes, anios_lectivos, grados,
    categorias_alerta, alertas, citas, sms_envios
TO alertas_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    familiares, grupos, ubicaciones, citas_alertas, notificaciones
TO alertas_app;

GRANT SELECT, INSERT ON bitacora TO alertas_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO alertas_app;
