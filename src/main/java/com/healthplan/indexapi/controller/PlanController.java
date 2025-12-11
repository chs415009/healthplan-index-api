package com.healthplan.indexapi.controller;

import com.healthplan.indexapi.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE, // 415 if not JSON payload
            produces = MediaType.APPLICATION_JSON_VALUE // 406 if no Accept header: application/json
    )
    public ResponseEntity<String> createPlan(@RequestBody String requestBody) {

        String objectId = planService.createPlan(requestBody);
        String savedPlan = planService.getPlan(objectId);
        String etag = planService.generateETag(savedPlan);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.ETAG, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .body(savedPlan);
    }


    @GetMapping(
            value = "/{objectId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> getPlan(
            @PathVariable String objectId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        String plan = planService.getPlan(objectId);
        String currentETag = planService.generateETag(plan);

        if (ifNoneMatch != null && ifNoneMatch.equals(currentETag)) { // if match 304 Not Modified
            return ResponseEntity
                    .status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, currentETag)
                    .build();
        }

        return ResponseEntity // if not match 200 OK
                .ok()
                .header(HttpHeaders.ETAG, currentETag)
                .contentType(MediaType.APPLICATION_JSON)
                .body(plan);
    }


    @DeleteMapping("/{objectId}")
    public ResponseEntity<Void> deletePlan(@PathVariable String objectId) {
        planService.deletePlan(objectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(
            value = "/{objectId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> patchPlan(
            @PathVariable String objectId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody String patchBody) {

        // 1. Get existing plan (會自動拋出 ResourceNotFoundException 如果不存在)
        String existingPlan = planService.getPlan(objectId);

        // 2. Conditional PATCH: Check If-Match header
        if (ifMatch != null) {
            String currentETag = planService.generateETag(existingPlan);
            if (!ifMatch.equals(currentETag)) {
                // ETag 不匹配 → 412 Precondition Failed
                return ResponseEntity
                        .status(HttpStatus.PRECONDITION_FAILED)
                        .build();
            }
        }

        // 3. Apply patch
        String patchedPlan = planService.patchPlan(objectId, patchBody);

        // 4. Generate new ETag
        String newETag = planService.generateETag(patchedPlan);

        return ResponseEntity
                .ok()
                .header(HttpHeaders.ETAG, newETag)
                .contentType(MediaType.APPLICATION_JSON)
                .body(patchedPlan);
    }
}
