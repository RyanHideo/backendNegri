package com.sinapse.ccm.modbus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
public class TagCatalog {
    @Getter
    private final List<TagDef> tags = new ArrayList<>();
    private final Map<String, TagDef> byName = new HashMap<>();

    @PostConstruct
    void load() {
        try {
            var res = new ClassPathResource("tags.yml");
            if (!res.exists()) {
                throw new IllegalStateException("Arquivo tags.yml não encontrado em src/main/resources");
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream in = res.getInputStream()) {
                JsonNode root = mapper.readTree(in);
                JsonNode array = root;

                // aceita:
                //   tags: [ ... ]
                // ou:
                //   - name: ...
                if (root.isObject() && root.has("tags")) {
                    array = root.get("tags");
                }
                if (array == null || !array.isArray()) {
                    throw new IllegalStateException("Estrutura inválida em tags.yml: esperava lista em 'tags:' ou array na raiz.");
                }

                int idx = -1;
                for (JsonNode n : array) {
                    idx++;
                    try {
                        TagDef t = new TagDef();

                        String name = required(n, "name", idx);
                        t.setName(name);

                        String typeStr = required(n, "type", idx);
                        t.setType(TagType.valueOf(typeStr.toUpperCase()));

                        int address = requiredAddressFlexible(n, idx, name);
                        t.setAddress(address);

                        if (n.has("unitId"))      t.setUnitId(n.get("unitId").asInt());
                        if (n.has("scale"))       t.setScale(n.get("scale").asDouble());
                        if (n.has("writable"))    t.setWritable(n.get("writable").asBoolean());
                        if (n.has("readable"))    t.setReadable(n.get("readable").asBoolean());
                        if (n.has("polled"))      t.setPolled(n.get("polled").asBoolean());
                        if (n.has("words"))       t.setWords(n.get("words").asInt());
                        if (n.has("format"))      t.setFormat(n.get("format").asText());
                        if (n.has("endian"))      t.setEndian(n.get("endian").asText());
                        if (n.has("description")) t.setDescription(n.get("description").asText());

                        tags.add(t);
                        byName.put(t.getName(), t);
                    } catch (Exception tagEx) {
                        // Log claro apontando exatamente qual item quebrou
                        log.error("Falha ao carregar tag no índice {}: conteúdo = {}", idx, n);
                        throw tagEx;
                    }
                }
            }

            log.info("Tags carregadas com sucesso: {}", tags.size());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar tags.yml", e);
        }
    }

    public TagDef byName(String name) {
        return byName.get(name);
    }

    // -------- helpers --------
    private static String required(JsonNode n, String field, int idx) {
        if (!n.hasNonNull(field)) {
            throw new IllegalStateException("Campo obrigatório ausente: " + field + " (índice " + idx + ")");
        }
        return n.get(field).asText();
    }

    /** Aceita 'address' e sinônimos 'addr'/'ref'. */
    private static int requiredAddressFlexible(JsonNode n, int idx, String name) {
        String[] keys = {"address", "addr", "ref"};
        for (String k : keys) {
            if (n.hasNonNull(k)) {
                String raw = n.get(k).asText().trim();
                try {
                    // aceita número em string
                    return Integer.parseInt(raw);
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Campo '" + k + "' inválido para tag '" + name +
                            "' (índice " + idx + "): valor='" + raw + "' não é inteiro.");
                }
            }
        }
        throw new IllegalStateException("Campo obrigatório ausente: address/addr/ref para tag '" + name + "' (índice " + idx + ")");
    }
}
