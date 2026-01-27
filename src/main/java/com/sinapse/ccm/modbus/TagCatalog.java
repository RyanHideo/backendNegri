package com.sinapse.ccm.modbus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TagCatalog {
    @Getter
    private final List<TagDef> tags = new ArrayList<>();
    private final Map<String, TagDef> byName = new HashMap<>();

    public TagCatalog(String resourcePath) {
        try {
            var res = new ClassPathResource(resourcePath);
            if (!res.exists()) {
                throw new IllegalStateException("Arquivo de tags não encontrado em src/main/resources: " + resourcePath);
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try (InputStream in = res.getInputStream()) {
                JsonNode root = mapper.readTree(in);
                JsonNode array = root;

                if (root.isObject() && root.has("tags")) {
                    array = root.get("tags");
                }
                if (array == null || !array.isArray()) {
                    throw new IllegalStateException("Estrutura inválida em " + resourcePath + ": esperava lista em 'tags:' ou array na raiz.");
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
                        log.error("Falha ao carregar tag no índice {} do arquivo {}: conteúdo = {}", idx, resourcePath, n);
                        throw tagEx;
                    }
                }
            }

            log.info("Tags carregadas com sucesso de {}: {}", resourcePath, tags.size());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar " + resourcePath, e);
        }
    }

    public TagDef byName(String name) {
        return byName.get(name);
    }

    private static String required(JsonNode n, String field, int idx) {
        if (!n.hasNonNull(field)) {
            throw new IllegalStateException("Campo obrigatório ausente: " + field + " (índice " + idx + ")");
        }
        return n.get(field).asText();
    }

    private static int requiredAddressFlexible(JsonNode n, int idx, String name) {
        String[] keys = {"address", "addr", "ref"};
        for (String k : keys) {
            if (n.hasNonNull(k)) {
                String raw = n.get(k).asText().trim();
                try {
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
