package com.omnia.backend.controller;
import com.omnia.backend.service.impl.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;
@RestController
@RequestMapping("/api/push")
@PreAuthorize("isAuthenticated()")
public class PushController {
 private final PushPreferences prefs;private final PushDevices devices;
 public PushController(PushPreferences prefs,PushDevices devices){this.prefs=prefs;this.devices=devices;}
 @GetMapping("/preferences")public PushPreferences.Response get(){return prefs.get();}
 @PutMapping("/preferences")public PushPreferences.Response save(@RequestBody PushPreferences.Values body){return prefs.save(body);}
 @PutMapping("/devices")public Map<String,Boolean> device(@RequestBody PushDevices.Registration body){devices.register(body);return Map.of("success",true);}
}
