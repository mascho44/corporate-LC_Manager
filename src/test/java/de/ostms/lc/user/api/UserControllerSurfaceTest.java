package de.ostms.lc.user.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerSurfaceTest {
 @Test void onlyTheListOfAssignablePeopleIsOfferedAndNoAccountCanBeCreatedChangedOrDeletedHere(){
  var mapped=Arrays.stream(UserController.class.getDeclaredMethods()).filter(m->Arrays.stream(m.getAnnotations()).anyMatch(a->a instanceof GetMapping||a instanceof PostMapping||a instanceof PutMapping||a instanceof DeleteMapping||a instanceof PatchMapping||a instanceof RequestMapping)).map(Method::getName).toList();
  assertThat(mapped).containsExactly("assignable");
  assertThat(UserController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/users");
 }
}
