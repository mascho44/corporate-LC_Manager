package de.ostms.lc.capture;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CaptureUploadController {
 private final CaptureUploadService uploads;
 public CaptureUploadController(CaptureUploadService uploads){this.uploads=uploads;}

 /** Delivery from a scan station (also usable with a normal login). Fields: file, batchId, scanner, pages, scannedAt (ISO-8601), sha256. */
 @PostMapping(value="/api/capture/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.ACCEPTED)
 public List<CaptureUploadService.Receipt> upload(@RequestPart("file") List<MultipartFile> files,@RequestParam(required=false) String batchId,@RequestParam(required=false) String scanner,
   @RequestParam(required=false) Integer pages,@RequestParam(required=false) String scannedAt,@RequestParam(required=false) String sha256,Authentication authentication)throws IOException{
  return uploads.receive(files,new CaptureUploadService.Meta(batchId,scanner,pages,scannedAt,sha256),authentication.getName());
 }

 @GetMapping("/api/inbox-capture") public Map<UUID,CaptureUploadService.Info> overview(){return uploads.overview();}
}
