package com.medical.agent.model.repository;

import com.medical.agent.model.entity.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

    List<ForumPost> findAllByOrderByCreatedAtDesc();
}
