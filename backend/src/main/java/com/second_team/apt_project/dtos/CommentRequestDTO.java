package com.second_team.apt_project.dtos;

import lombok.Getter;

@Getter
public class CommentRequestDTO {
    private String content;
    private Long articleId;
    private Long parentId;
    private Long commentId;
}
