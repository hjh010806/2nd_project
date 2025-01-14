package com.second_team.apt_project.repositories.customs.impls;

import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.second_team.apt_project.domains.Propose;
import com.second_team.apt_project.domains.QPropose;
import com.second_team.apt_project.enums.ProposeStatus;
import com.second_team.apt_project.repositories.customs.ProposeRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class ProposeRepositoryImpl implements ProposeRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;
    QPropose qPropose = QPropose.propose;

    @Override
    public Page<Propose> findList(Pageable pageable, int status) {
        ProposeStatus proposeStatus = ProposeStatus.values()[status];
        QueryResults<Propose> results = jpaQueryFactory.selectFrom(qPropose)
                .where(qPropose.proposeStatus.eq(proposeStatus))
                .orderBy(qPropose.createDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetchResults();
        return new PageImpl<>(results.getResults(), pageable, results.getTotal());
    }
}
