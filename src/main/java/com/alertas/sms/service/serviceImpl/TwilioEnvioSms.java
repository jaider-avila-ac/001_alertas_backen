package com.alertas.sms.service.serviceImpl;

import com.alertas.sms.service.EnvioSms;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// api rest de Twilio, sin su libreria: un POST a Messages.json con usuario y clave de la cuenta
public class TwilioEnvioSms implements EnvioSms {

    private static final String URL = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final String sid;
    private final String numero;
    private final String autorizacion;
    private final RestClient cliente;
    private final ObjectMapper mapper;

    public TwilioEnvioSms(String sid, String token, String numero, ObjectMapper mapper) {

        this.sid = sid;
        this.numero = numero;
        this.autorizacion = "Basic " + Base64.getEncoder()
                .encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        this.cliente = RestClient.create();
        this.mapper = mapper;
    }

    @Override
    public ResultadoEnvio enviar(String celular, String texto) {

        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("To", celular);
        formulario.add("From", numero);
        formulario.add("Body", texto);

        try {
            String respuesta = cliente.post()
                    .uri(String.format(URL, sid))
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(String.class);

            JsonNode json = mapper.readTree(respuesta);
            return new ResultadoEnvio(true, json.path("sid").asText(null), null);
        } catch (RestClientResponseException e) {
            // twilio explica el error en el cuerpo (ej. numero no verificado en cuenta de prueba)
            return new ResultadoEnvio(false, null, mensajeDeTwilio(e));
        } catch (Exception e) {
            return new ResultadoEnvio(false, null, recortar(e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private String mensajeDeTwilio(RestClientResponseException e) {

        try {
            JsonNode json = mapper.readTree(e.getResponseBodyAsString());
            return recortar(json.path("code").asText() + " " + json.path("message").asText());
        } catch (Exception otro) {
            return recortar("HTTP " + e.getStatusCode().value());
        }
    }

    private String recortar(String texto) {

        if (texto == null) {
            return null;
        }
        if (texto.length() > 300) {
            return texto.substring(0, 300);
        }
        return texto;
    }
}
