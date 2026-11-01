package com.omnia.backend.controller;
import com.omnia.backend.service.impl.NotificationInbox;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
 private final NotificationInbox inbox;
 public NotificationController(NotificationInbox inbox){this.inbox=inbox;}
 @GetMapping public NotificationInbox.Page list(@RequestParam(defaultValue="all")String filter,@RequestParam(defaultValue="0")long before){return inbox.list(filter,before);}
 @PutMapping("/read-all") public Map<String,Boolean> readAll(@RequestParam long throughId){inbox.readAll(throughId);return Map.of("success",true);}
 @PostMapping("/{id}/open") public Map<String,Object> open(@PathVariable long id){return inbox.open(id);}
}
