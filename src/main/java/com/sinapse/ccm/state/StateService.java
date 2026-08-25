package com.sinapse.ccm.state;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Properties;

@Slf4j
@Service
public class StateService {

    // Mantém a chave antiga no arquivo para não perder o estado já persistido.
    private static final String TIMESTAMP_KEY = "last.consumption.reset.timestamp.ccm1";
    private final Path stateFilePath = Paths.get("data", "app_state.properties");

    private volatile Instant lastResetTimestamp;

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(stateFilePath.getParent());
            if (Files.exists(stateFilePath)) {
                loadState();
            } else {
                log.info("Arquivo de estado não encontrado em '{}'. Será criado no primeiro reset.", stateFilePath);
            }
        } catch (IOException e) {
            log.error("Falha ao inicializar o diretório de estado.", e);
        }
    }

    private void loadState() {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(stateFilePath)) {
            props.load(input);
            String timestampValue = props.getProperty(TIMESTAMP_KEY);
            if (timestampValue != null && !timestampValue.isEmpty()) {
                lastResetTimestamp = Instant.parse(timestampValue);
                log.info("Data do último reset de consumo carregada: {}", lastResetTimestamp);
            }
        } catch (IOException e) {
            log.error("Falha ao carregar o estado do arquivo '{}'.", stateFilePath, e);
        }
    }

    private void saveState() {
        Properties props = new Properties();
        if (lastResetTimestamp != null) {
            props.setProperty(TIMESTAMP_KEY, lastResetTimestamp.toString());
        }

        try (OutputStream output = Files.newOutputStream(stateFilePath)) {
            props.store(output, "Application State - Do not edit manually unless you know what you are doing");
            log.info("Estado da aplicação salvo com sucesso em '{}'.", stateFilePath);
        } catch (IOException e) {
            log.error("Falha ao salvar o estado no arquivo '{}'.", stateFilePath, e);
        }
    }

    public Instant getLastResetTimestamp() {
        return lastResetTimestamp;
    }

    public void updateAndPersistResetTimestamp() {
        lastResetTimestamp = Instant.now();
        log.info("Atualizando data do reset de consumo: {}", lastResetTimestamp);
        saveState();
    }
}
