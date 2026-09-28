package com.alertas.superadmin.config;

import com.alertas.superadmin.service.SuperadminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// al arrancar crea el primer superadmin con los datos del .env, si todavia no hay ninguno
@Component
public class SuperadminInicial implements ApplicationRunner {

    private final SuperadminService superadminService;
    private final String usuario;
    private final String contrasena;
    private final String nombres;

    public SuperadminInicial(
            SuperadminService superadminService,
            @Value("${app.superadmin.usuario:}") String usuario,
            @Value("${app.superadmin.contrasena:}") String contrasena,
            @Value("${app.superadmin.nombres:}") String nombres) {

        this.superadminService = superadminService;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.nombres = nombres;
    }

    @Override
    public void run(ApplicationArguments args) {
        superadminService.crearInicialSiNoExiste(usuario, contrasena, nombres);
    }
}
