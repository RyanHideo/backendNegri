package com.sinapse.ccm.motors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@Getter
public class MotorCatalog {

    private List<MotorDef> motors = Collections.emptyList();

    @PostConstruct
    void load() {
        try {
            var res = new ClassPathResource("motors.yml");
            if (!res.exists()) {
                log.warn("Arquivo motors.yml não encontrado. Nenhum motor será carregado para cálculo de eficiência.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream in = res.getInputStream()) {
                // Mapeia a estrutura 'motors: [ ... ]' para a lista de MotorDef
                MotorFile a = mapper.readValue(in, MotorFile.class);
                if (a != null && a.getMotors() != null) {
                    this.motors = a.getMotors();
                }
            }

            log.info("Motores carregados para cálculo de eficiência: {}", motors.size());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar e processar motors.yml", e);
        }
    }

    // Classe auxiliar para mapear a raiz do arquivo YAML
    @Getter
    private static class MotorFile {
        private List<MotorDef> motors;

        public void setMotors(List<MotorDef> motors) {
            this.motors = motors;
        }
    }
}
