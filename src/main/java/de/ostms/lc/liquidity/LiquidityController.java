package de.ostms.lc.liquidity;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** Liquidity preview, only for tenants with the corporate profile (see TenantModulePolicy). */
@RestController @RequestMapping("/api/liquidity")
public class LiquidityController {
 private final LiquidityService service;
 public LiquidityController(LiquidityService service){this.service=service;}
 @GetMapping public LiquidityService.Response preview(@RequestParam(defaultValue="WEEK") LiquidityCalculator.Granularity granularity,@RequestParam(required=false) Integer periods){
  return service.preview(granularity,periods!=null?periods:(granularity==LiquidityCalculator.Granularity.WEEK?13:6),LocalDate.now());
 }
}
