package com.sinapse.ccm.truckflow;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/truck-flow")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173"})
public class TrafficLightController {

    private final TrafficLightService trafficLightService;

    @GetMapping
    public TrafficLightSnapshot getCurrentState() {
        return trafficLightService.getCurrentSnapshot();
    }
}
