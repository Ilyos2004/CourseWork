package com.cw.tutoring.repository;

import com.cw.tutoring.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

    @Query(value = "SELECT fn_add_review(:bookingId, :rating, :comment)", nativeQuery = true)
    Integer addReviewViaFunction(
            @Param("bookingId") Integer bookingId,
            @Param("rating") Integer rating,
            @Param("comment") String comment
    );

    // удалить отзывы по студенту
    void deleteByStudent_Id(Integer studentId);

    // удалить отзывы по репетитору
    void deleteByTutorSubject_Tutor_Id(Integer tutorId);
}
