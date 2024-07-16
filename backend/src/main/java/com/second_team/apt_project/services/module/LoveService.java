package com.second_team.apt_project.services.module;

import com.second_team.apt_project.domains.Article;
import com.second_team.apt_project.domains.Love;
import com.second_team.apt_project.domains.Profile;
import com.second_team.apt_project.repositories.LoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoveService {
    private final LoveRepository loveRepository;

    public void save(Article article, Profile profile) {
        loveRepository.save(Love.builder()
                .article(article)
                .profile(profile).build());
    }

    public Love findByArticleAndProfile(Article article, Profile profile) {
        return loveRepository.findByArticleAndProfile(article, profile).orElse(null);
    }

    public void delete(Love love) {
        loveRepository.delete(love);
    }

    public List<Love> findByArticle(Long id) {
        return this.loveRepository.findByArticle(id);
    }
}