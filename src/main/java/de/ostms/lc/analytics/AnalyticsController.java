package de.ostms.lc.analytics;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** Trend numbers for the cockpit; reading is open to signed-in users, the files they may not see are not counted. */
@RestController @RequestMapping("/api/analytics")
public class AnalyticsController {
 private final AnalyticsService service;
 public AnalyticsController(AnalyticsService service){this.service=service;}
 @GetMapping public AnalyticsService.Response trends(@RequestParam(defaultValue="90") int days){return service.trends(days,LocalDate.now());}
}
