package com.cw.tutoring.controller;

import com.cw.tutoring.dto.ReviewRequest;
import com.cw.tutoring.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<Integer> addReview(@RequestBody ReviewRequest request) {
        Integer reviewId = reviewService.addReview(request);
        return ResponseEntity.ok(reviewId);
    }
}
