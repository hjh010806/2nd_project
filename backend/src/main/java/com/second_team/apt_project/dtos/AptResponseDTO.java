package com.second_team.apt_project.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class AptResponseDTO {
    private Long aptId;
    private String roadAddress; // 도로명주소
    private String aptName; // 아파트 이름
    private List<ImageListResponseDTO> urlList;

    @Builder
    public AptResponseDTO(Long aptId, String roadAddress, String aptName, List<ImageListResponseDTO> urlList) {
        this.aptId = aptId;
        this.roadAddress = roadAddress;
        this.aptName = aptName;
        this.urlList = urlList;
    }
}
