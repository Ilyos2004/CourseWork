package com.cw.tutoring.service;

import com.cw.tutoring.dto.ReviewRequest;
import com.cw.tutoring.repository.ReviewRepository;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public Integer addReview(ReviewRequest request) {
        return reviewRepository.addReviewViaFunction(
                request.getBookingId(),
                request.getRating(),
                request.getComment()
        );
    }
}
