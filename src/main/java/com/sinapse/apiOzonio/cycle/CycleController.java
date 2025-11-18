package com.sinapse.apiOzonio.cycle;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cycle")
public class CycleController {

    private final CycleTimerService cycle;

    @GetMapping
    public CycleTimerService.CycleSnapshot get() {
        return cycle.snapshot();
    }

}
