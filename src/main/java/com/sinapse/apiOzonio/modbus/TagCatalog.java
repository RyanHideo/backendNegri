package com.sinapse.apiOzonio.modbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class TagCatalog {
    @Getter
    private List<TagDef> tags = new ArrayList<>();
    private Map<String, TagDef> byName = new HashMap<>();

    @PostConstruct
    void load() {
        try {
            var res = new ClassPathResource("tags.yml");
            var mapper = new ObjectMapper(new YAMLFactory());
            var root = mapper.readTree(res.getInputStream());
            root.get("tags").forEach(n -> {
                var t = new TagDef();
                t.setName(n.get("name").asText());
                t.setType(TagDef.Kind.valueOf(n.get("type").asText()));
                t.setAddress(n.get("address").asInt());
                if (n.has("unitId")) t.setUnitId(n.get("unitId").asInt());
                if (n.has("scale"))  t.setScale(n.get("scale").asDouble());
                if (n.has("writable")) t.setWritable(n.get("writable").asBoolean());
                tags.add(t);
                byName.put(t.getName(), t);
            });
            log.info("Tags carregadas: {}", tags.size());
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar tags.yml", e);
        }
    }

    public TagDef byName(String name) { return byName.get(name); }
}