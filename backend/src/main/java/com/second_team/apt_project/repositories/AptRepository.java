package com.second_team.apt_project.repositories;

import com.second_team.apt_project.domains.Apt;
import com.second_team.apt_project.repositories.customs.AptRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AptRepository extends JpaRepository<Apt, Long>, AptRepositoryCustom {

}
