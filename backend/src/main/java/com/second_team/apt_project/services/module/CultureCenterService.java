package com.second_team.apt_project.services.module;

import com.second_team.apt_project.domains.Apt;
import com.second_team.apt_project.domains.CultureCenter;
import com.second_team.apt_project.enums.CenterType;
import com.second_team.apt_project.repositories.CultureCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CultureCenterService {
    private final CultureCenterRepository cultureCenterRepository;

    public CultureCenter save(int type, LocalDateTime endDate, LocalDateTime startDate, Apt apt) {
        return cultureCenterRepository.save(CultureCenter.builder()
                .centerType(CenterType.values()[type])
                .openTime(startDate)
                .closeTime(endDate)
                .apt(apt)
                .build());
    }

    public CultureCenter findById(Long centerId) {
        return cultureCenterRepository.findById(centerId).orElse(null);
    }

    public void update(CultureCenter cultureCenter, int type, LocalDateTime endDate, LocalDateTime startDate) {
        cultureCenter.setCenterType(CenterType.values()[type]);
        cultureCenter.setOpenTime(startDate);
        cultureCenter.setCloseTime(endDate);
        cultureCenter.setModifyDate(LocalDateTime.now());
        cultureCenterRepository.save(cultureCenter);
    }

    public void delete(CultureCenter cultureCenter) {
        cultureCenterRepository.delete(cultureCenter);
    }

    public List<CultureCenter> getList(Long aptId) {
        return cultureCenterRepository.getList(aptId);
    }
}
