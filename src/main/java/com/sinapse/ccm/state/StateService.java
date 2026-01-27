package com.sinapse.ccm.state;

import com.sinapse.ccm.modbus.ModbusService;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class StateService {

    private static final String TIMESTAMP_KEY_PREFIX = "last.consumption.reset.timestamp.";
    private final Path stateFilePath = Paths.get("data", "app_state.properties");

    // Usar um mapa para armazenar a data de reset para cada CCM
    private final Map<String, Instant> lastResetTimestamps = new ConcurrentHashMap<>();

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
            // Itera sobre as chaves do enum CcmKey para carregar os dados de cada CCM
            for (ModbusService.CcmKey ccmKey : ModbusService.CcmKey.values()) {
                String key = ccmKey.getKey();
                String propertyKey = TIMESTAMP_KEY_PREFIX + key;
                String timestampStr = props.getProperty(propertyKey);
                if (timestampStr != null && !timestampStr.isEmpty()) {
                    Instant timestamp = Instant.parse(timestampStr);
                    this.lastResetTimestamps.put(key, timestamp);
                    log.info("Data do último reset de consumo para {} carregada: {}", key, timestamp);
                }
            }
        } catch (IOException e) {
            log.error("Falha ao carregar o estado do arquivo '{}'.", stateFilePath, e);
        }
    }

    private void saveState() {
        Properties props = new Properties();
        // Salva a data de cada CCM no arquivo de propriedades
        this.lastResetTimestamps.forEach((ccmKey, timestamp) -> {
            if (timestamp != null) {
                props.setProperty(TIMESTAMP_KEY_PREFIX + ccmKey, timestamp.toString());
            }
        });

        try (OutputStream output = Files.newOutputStream(stateFilePath)) {
            props.store(output, "Application State - Do not edit manually unless you know what you are doing");
            log.info("Estado da aplicação salvo com sucesso em '{}'.", stateFilePath);
        } catch (IOException e) {
            log.error("Falha ao salvar o estado no arquivo '{}'.", stateFilePath, e);
        }
    }

    public Instant getLastResetTimestamp(String ccmKey) {
        return this.lastResetTimestamps.get(ccmKey);
    }

    public void updateAndPersistResetTimestamp(String ccmKey) {
        Instant now = Instant.now();
        this.lastResetTimestamps.put(ccmKey, now);
        log.info("Atualizando data do reset de consumo para {}: {}", ccmKey, now);
        saveState();
    }
}
