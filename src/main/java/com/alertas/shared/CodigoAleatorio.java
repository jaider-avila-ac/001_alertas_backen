package com.alertas.shared;

import java.security.SecureRandom;

// codigo que va en las urls en vez del id. 12 caracteres de 62 posibles: no se puede adivinar
public final class CodigoAleatorio {

    private static final String LETRAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LARGO = 12;
    private static final SecureRandom AZAR = new SecureRandom();

    private CodigoAleatorio() {
    }

    public static String generar() {

        StringBuilder codigo = new StringBuilder(LARGO);

        for (int i = 0; i < LARGO; i++) {
            int posicion = AZAR.nextInt(LETRAS.length());
            codigo.append(LETRAS.charAt(posicion));
        }

        return codigo.toString();
    }
}
