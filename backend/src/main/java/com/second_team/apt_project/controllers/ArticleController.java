package com.second_team.apt_project.controllers;

import com.second_team.apt_project.dtos.ArticleRequestDTO;
import com.second_team.apt_project.dtos.ArticleResponseDTO;
import com.second_team.apt_project.exceptions.DataNotFoundException;
import com.second_team.apt_project.records.TokenRecord;
import com.second_team.apt_project.services.MultiService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/article")
public class ArticleController {
    private final MultiService multiService;

    @PostMapping
    public ResponseEntity<?> saveArticle(@RequestHeader("Authorization") String accessToken,
                                         @RequestHeader("PROFILE_ID") Long profileId,
                                         @RequestBody ArticleRequestDTO articleRequestDTO) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                ArticleResponseDTO articleResponseDTO = this.multiService.saveArticle(profileId,
                        articleRequestDTO.getCategoryId(), articleRequestDTO.getTagName(), articleRequestDTO.getTitle(),
                        articleRequestDTO.getContent(), username, articleRequestDTO.getTopActive());
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTO);
            }
        } catch (DataNotFoundException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @PutMapping
    public ResponseEntity<?> updateArticle(@RequestHeader("Authorization") String accessToken,
                                           @RequestHeader("PROFILE_ID") Long profileId,
                                           @RequestBody ArticleRequestDTO articleRequestDTO) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                ArticleResponseDTO articleResponseDTO = this.multiService.updateArticle(profileId, articleRequestDTO.getArticleId(),
                        articleRequestDTO.getCategoryId(), articleRequestDTO.getTagName(), articleRequestDTO.getTitle(), articleRequestDTO.getArticleTagId(),
                        articleRequestDTO.getContent(), username, articleRequestDTO.getTopActive());
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTO);
            }
        } catch (IllegalArgumentException | DataNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @GetMapping
    public ResponseEntity<?> articleDetail(@RequestHeader("Authorization") String accessToken,
                                           @RequestHeader("PROFILE_ID") Long profileId,
                                           @RequestHeader("ArticleId") Long articleId) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                ArticleResponseDTO articleResponseDTO = this.multiService.articleDetail(articleId, profileId, username);
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTO);
            }
        } catch (DataNotFoundException | IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @GetMapping("/list")
    public ResponseEntity<?> articleList(@RequestHeader("Authorization") String accessToken,
                                         @RequestHeader("PROFILE_ID") Long profileId,
                                         @RequestHeader("CategoryId") Long categoryId,
                                         @RequestHeader(value = "Page", defaultValue = "0") int page,
                                         @RequestHeader(value = "AptId", defaultValue = "0") Long aptId) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                Page<ArticleResponseDTO> articleResponseDTOList = this.multiService.articleList(username, aptId, page, profileId, categoryId);
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTOList);
            }
        } catch (IllegalArgumentException | DataNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @GetMapping("/topActive")
    public ResponseEntity<?> topActive(@RequestHeader("Authorization") String accessToken,
                                       @RequestHeader("PROFILE_ID") Long profileId,
                                       @RequestHeader("CategoryId") Long categoryId,
                                       @RequestHeader("AptId") Long aptId) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                List<ArticleResponseDTO> articleResponseDTOList = this.multiService.topActive(username, aptId, profileId, categoryId);
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTOList);
            }
        } catch (IllegalArgumentException | DataNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @DeleteMapping
    public ResponseEntity<?> deleteArticle(@RequestHeader("Authorization") String accessToken,
                                           @RequestHeader("PROFILE_ID") Long profileId,
                                           @RequestHeader("ArticleId") Long articleId) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                this.multiService.deleteArticle(username, profileId, articleId);
                return ResponseEntity.status(HttpStatus.OK).body("문제 없음");
            }
        } catch (IllegalArgumentException | DataNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchArticle(@RequestHeader("Authorization") String accessToken,
                                           @RequestHeader("PROFILE_ID") Long profileId,
                                           @RequestHeader("Page") int page,
                                           @RequestHeader(value = "Keyword", defaultValue = "") String encodedKeyword,
                                           @RequestHeader("Sort") int sort,
                                           @RequestHeader(value = "CategoryId", defaultValue = "") Long categoryId) {
        TokenRecord tokenRecord = this.multiService.checkToken(accessToken, profileId);
        try {
            if (tokenRecord.isOK()) {
                String username = tokenRecord.username();
                String keyword = URLDecoder.decode(encodedKeyword, StandardCharsets.UTF_8);
                Page<ArticleResponseDTO> articleResponseDTOList = this.multiService.searchArticle(username, profileId, page, keyword, sort, categoryId);
                return ResponseEntity.status(HttpStatus.OK).body(articleResponseDTOList);
            }
        } catch (IllegalArgumentException | DataNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        return tokenRecord.getResponseEntity();
    }
}
