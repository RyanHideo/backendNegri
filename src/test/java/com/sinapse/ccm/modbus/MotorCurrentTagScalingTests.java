package com.sinapse.ccm.modbus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MotorCurrentTagScalingTests {

    @Test
    void activeMotorCurrentTagsUseOneDecimalScale() {
        TagCatalog catalog = new TagCatalog("tags-ccm1.yml");
        List<String> activeCurrentTags = List.of(
                "M1_A", "M2_A", "M3_A", "M4_A", "M5_A",
                "M6_A", "M7_A", "M8_A", "M10_A"
        );

        for (String tagName : activeCurrentTags) {
            TagDef tag = catalog.byName(tagName);
            assertNotNull(tag, () -> "Tag de corrente ausente: " + tagName);
            assertEquals(0.1, tag.getScaleOrDefault(), 0.00001,
                    () -> "Escala incorreta para " + tagName);
        }
    }

    @Test
    void m8UsesD30ForCurrentAndD31ForFault() {
        TagCatalog catalog = new TagCatalog("tags-ccm1.yml");

        TagDef current = catalog.byName("M8_A");
        TagDef fault = catalog.byName("M8_F");

        assertNotNull(current);
        assertEquals(TagType.HR, current.getType());
        assertEquals(4031, current.getAddress());
        assertEquals(0.1, current.getScaleOrDefault(), 0.00001);

        assertNotNull(fault);
        assertEquals(TagType.HR, fault.getType());
        assertEquals(4032, fault.getAddress());
    }
}
