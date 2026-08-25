package com.sinapse.ccm.auxiliary;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ccm/auxiliary")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuxiliaryEquipmentController {

    private final AuxiliaryEquipmentService auxiliaryEquipmentService;

    @GetMapping
    public AuxiliaryEquipmentSnapshot getCurrentState() {
        return auxiliaryEquipmentService.snapshot();
    }
}
