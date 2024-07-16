package com.second_team.apt_project.services.module;

import com.second_team.apt_project.domains.Article;
import com.second_team.apt_project.domains.Comment;
import com.second_team.apt_project.domains.Profile;
import com.second_team.apt_project.repositories.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;

    public Comment saveComment(Article article, Profile profile, String content, Long parentId) {
        Comment parent = null;

        if (parentId != null) {
            Optional<Comment> comment = commentRepository.findByParentId(parentId);
            if (comment.isPresent())
                parent = comment.get();
        }
        return commentRepository.save(Comment.builder()
                .article(article)
                .content(content)
                .profile(profile)
                .parent(parent)
                .build());
    }

    public List<Comment> getComment(Long articleId) {
        return commentRepository.getComment(articleId);
    }

}
