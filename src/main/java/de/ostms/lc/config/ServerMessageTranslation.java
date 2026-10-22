package de.ostms.lc.config;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

/** Answers carrying a text for the screen ("error" or "message" in a JSON object) are translated when the browser asks for English (header X-LC-Language). */
@RestControllerAdvice
public class ServerMessageTranslation implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Map<?, ?> map) || !(map.get("error") instanceof String || map.get("message") instanceof String)) return body;
        String language = request.getHeaders().getFirst("X-LC-Language");
        if (!"en".equalsIgnoreCase(language == null ? "" : language.trim())) return body;
        var translated = new LinkedHashMap<Object, Object>(map);
        for (String key : new String[] {"error", "message"}) if (translated.get(key) instanceof String text) translated.put(key, ServerMessages.translate(text, language));
        return translated;
    }
}
