package com.sinapse.ccm.power;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/power")
@CrossOrigin(origins = "*")
public class PowerHistoryController {

    private final PowerHistoryService powerHistoryService;

    @GetMapping("/history")
    public PowerHistoryService.PowerHistorySnapshot getHistory() {
        return powerHistoryService.snapshot();
    }
}
