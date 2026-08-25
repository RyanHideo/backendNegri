package com.sinapse.ccm;

import com.sinapse.ccm.modbus.TagCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public TagCatalog ccmCatalog() {
        return new TagCatalog("tags-ccm1.yml");
    }
}
