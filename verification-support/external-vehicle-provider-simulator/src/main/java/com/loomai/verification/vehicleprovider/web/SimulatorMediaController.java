package com.loomai.verification.vehicleprovider.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class SimulatorMediaController {

    private static final MediaType WEBP = MediaType.parseMediaType("image/webp");
    private static final Set<String> ALLOWED_SIZES = Set.of("w300h225", "w720h540", "w1024h768");
    private static final Pattern IMAGE_ID = Pattern.compile("simulator-vehicle-(0[1-5])");

    @GetMapping(path = "/media/{size}/{imageId}.webp", produces = "image/webp")
    public ResponseEntity<Resource> media(@PathVariable String size, @PathVariable String imageId) {
        Matcher matcher = IMAGE_ID.matcher(imageId);
        if (!ALLOWED_SIZES.contains(size) || !matcher.matches()) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        Resource resource = new ClassPathResource(
            "simulator-media/vehicle-" + matcher.group(1) + ".webp"
        );
        if (!resource.exists()) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        return ResponseEntity.ok()
            .contentType(WEBP)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("Access-Control-Allow-Origin", "*")
            .header("Cross-Origin-Resource-Policy", "cross-origin")
            .header("X-Content-Type-Options", "nosniff")
            .body(resource);
    }
}
