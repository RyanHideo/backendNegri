package com.sinapse.ccm.truckflow;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

@Slf4j
@Component
public class TruckCountStore {

    private static final String COUNT_KEY = "truck.count";

    private final Path stateFilePath;
    private long count;

    public TruckCountStore(@Value("${truckflow.state-file:data/truck_count.properties}") String stateFile) {
        this.stateFilePath = Paths.get(stateFile);
    }

    @PostConstruct
    public synchronized void initialize() {
        try {
            Path parent = stateFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(stateFilePath)) {
                load();
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Falha ao carregar o contador de caminhoes de '{}'. O contador iniciara em zero.",
                    stateFilePath, e);
            count = 0;
        }
    }

    public synchronized long getCount() {
        return count;
    }

    public synchronized long incrementAndGet() {
        count++;
        save();
        return count;
    }

    private void load() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFilePath)) {
            properties.load(input);
        }
        count = Long.parseLong(properties.getProperty(COUNT_KEY, "0"));
        log.info("Contador de caminhoes carregado: {}.", count);
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(COUNT_KEY, Long.toString(count));

        Path temporaryFile = stateFilePath.resolveSibling(stateFilePath.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporaryFile)) {
            properties.store(output, "Truck flow state");
        } catch (IOException e) {
            log.error("Falha ao escrever o contador temporario em '{}'.", temporaryFile, e);
            return;
        }

        try {
            Files.move(temporaryFile, stateFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Falha ao persistir o contador de caminhoes em '{}'.", stateFilePath, e);
        }
    }
}
