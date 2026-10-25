package de.ostms.lc.user.api;

import de.ostms.lc.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Only the list of people a task can be assigned to. Creating, changing and deleting accounts is not offered here: accounts are created through invitations and the platform
 * administration, and tenant administrators manage membership and role under /api/users/memberships.
 */
@RestController @RequestMapping("/api/users")
public class UserController {
 private final UserService service;
 public UserController(UserService service){this.service=service;}

 @GetMapping("/assignable") public List<AssignableUser> assignable(){return service.assignable();}
}
