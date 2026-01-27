package com.sinapse.ccm;

import com.sinapse.ccm.modbus.TagCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public TagCatalog ccm1Catalog() {
        return new TagCatalog("tags-ccm1.yml");
    }

    @Bean
    public TagCatalog ccm2Catalog() {
        return new TagCatalog("tags-ccm2.yml");
    }
}
