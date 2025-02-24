package com.second_team.apt_project.services.module;

import com.second_team.apt_project.exceptions.DataDuplicateException;
import com.second_team.apt_project.domains.Apt;
import com.second_team.apt_project.domains.SiteUser;
import com.second_team.apt_project.enums.UserRole;
import com.second_team.apt_project.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SiteUser get(String value){
        return this.userRepository.findById(value).orElse(null);
    }

    public boolean isMatch(String password1, String password2) {
        return passwordEncoder.matches(password1, password2);
    }

    public void userEmailCheck(String email) {
        if (userRepository.isDuplicateEmail(email).size()>1) throw new DataDuplicateException("이베일 중복");
    }

    public SiteUser save(String name, String password, String email, int aptNumber, int role, Apt apt) {
        return userRepository.save(SiteUser.builder()
                .username(name)
                .password(passwordEncoder.encode(password))
                .email(email)
                .aptNum(aptNumber)
                .role(UserRole.values()[role])
                .apt(apt).build());
    }

    public SiteUser saveGroup(String name, int aptNumber, Apt apt) {
        return userRepository.save(SiteUser.builder()
                .username(String.valueOf(apt.getId()) + "_" + name)
                .password(passwordEncoder.encode(String.valueOf(apt.getId()) + "_" + name))
                .aptNum(aptNumber)
                .role(UserRole.values()[3])
                .apt(apt)
                .build());
    }

    @Transactional
    public Page<SiteUser> getUserList(Pageable pageable, Long aptId) {

        return this.userRepository.findByUserList(pageable,aptId);
    }

    public SiteUser getUser(String username) {
        return this.userRepository.findByUsername(username);
    }

    public SiteUser update(SiteUser updateUser, String email) {
        updateUser.setEmail(email);
        updateUser.setModifyDate(LocalDateTime.now());
        return userRepository.save(updateUser);
    }

    public void updatePassword(SiteUser user,  String newPassword1) {
        user.setPassword(passwordEncoder.encode(newPassword1));
        this.userRepository.save(user);
    }

    public void deleteUser(SiteUser user) {
        userRepository.delete(user);
    }
}
