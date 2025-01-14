package com.second_team.apt_project.services;

import com.second_team.apt_project.AptProjectApplication;
import com.second_team.apt_project.domains.*;
import com.second_team.apt_project.dtos.*;
import com.second_team.apt_project.enums.ImageKey;
import com.second_team.apt_project.enums.Sorts;
import com.second_team.apt_project.enums.UserRole;
import com.second_team.apt_project.exceptions.DataDuplicateException;
import com.second_team.apt_project.exceptions.DataNotFoundException;
import com.second_team.apt_project.records.TokenRecord;
import com.second_team.apt_project.securities.CustomUserDetails;
import com.second_team.apt_project.securities.jwt.JwtTokenProvider;
import com.second_team.apt_project.services.module.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MultiService {
    private final UserService userService;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AptService aptService;
    private final FileSystemService fileSystemService;
    private final ProfileService profileService;
    private final CategoryService categoryService;
    private final MultiKeyService multiKeyService;
    private final ArticleService articleService;
    private final TagService tagService;
    private final ArticleTagService articleTagService;
    private final LoveService loveService;
    private final CommentService commentService;
    private final CultureCenterService cultureCenterService;
    private final LessonService lessonService;
    private final LessonUserService lessonUserService;
    private final ChatRoomService chatRoomService;
    private final ChatRoomUserService chatRoomUserService;
    private final ChatMessageService chatMessageService;
    private final ProposeService proposeService;

    /**
     * Auth
     */

    public TokenRecord checkToken(String accessToken) {
        HttpStatus httpStatus = HttpStatus.FORBIDDEN;
        String username = null;
        String body = "logout";
        if (accessToken != null && accessToken.length() > 7) {
            String token = accessToken.substring(7);
            if (this.jwtTokenProvider.validateToken(token)) {
                httpStatus = HttpStatus.OK;
                username = this.jwtTokenProvider.getUsernameFromToken(token);
                body = "okay";
            } else {
                httpStatus = HttpStatus.UNAUTHORIZED;
                body = "refresh";
            }
        }
        return TokenRecord.builder().httpStatus(httpStatus).username(username).body(body).build();
    }

    public TokenRecord checkToken(String accessToken, Long profile_id) {
        if (profile_id == null)
            return TokenRecord.builder().httpStatus(HttpStatus.UNAUTHORIZED).body("unknown profile").build();
        return checkToken(accessToken);
    }

    @Transactional
    public String refreshToken(String refreshToken) {
        if (this.jwtTokenProvider.validateToken(refreshToken)) {
            String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
            SiteUser user = userService.get(username);
            if (user != null) {
                return this.jwtTokenProvider.generateAccessToken(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), user.getPassword()));
            }
        }
        return null;
    }

    @Transactional
    public AuthResponseDTO login(AuthRequestDTO requestDto) {
        SiteUser user = this.userService.get(requestDto.getUsername());
        if (user == null) {
            throw new IllegalArgumentException("유저 객체 없음");
        }
        if (!this.userService.isMatch(requestDto.getPassword(), user.getPassword()))
            throw new IllegalArgumentException("password");
        String accessToken = this.jwtTokenProvider //
                .generateAccessToken(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), user.getPassword()));
        String refreshToken = this.jwtTokenProvider //
                .generateRefreshToken(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), user.getPassword()));

        return AuthResponseDTO.builder().tokenType("Bearer").accessToken(accessToken).refreshToken(refreshToken).build();
    }

    /**
     * User
     */
    @Transactional
    private void userCheck(SiteUser user, Profile profile) {
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        if (profile == null) throw new DataNotFoundException("프로필 객체 없음");
        if (profile.getUser() != user) throw new IllegalArgumentException("유저와 일치 X");
    }

    @Transactional
    private UserResponseDTO getUserResponseDTO(SiteUser siteUser) {
        return UserResponseDTO.builder()
                .aptNum(siteUser.getAptNum())
                .username(siteUser.getUsername())
                .email(siteUser.getEmail())
                .aptResponseDTO(this.getAptResponseDTO(siteUser.getApt()))
                .createDate(this.dateTimeTransfer(siteUser.getCreateDate()))
                .modifyDate(this.dateTimeTransfer(siteUser.getModifyDate()))
                .role(siteUser.getRole().toString())
                .build();
    }

    @Transactional
    public UserResponseDTO saveUser(String name, String password, String email, int aptNumber, int role, Long aptId, String username, Long profileId) {
            SiteUser user = userService.get(username);
            Profile profile = profileService.findById(profileId);
            this.userCheck(user, profile);
            Apt apt = aptService.get(aptId);
            if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
            if (user.getRole() != UserRole.ADMIN)
                if (!user.getApt().equals(apt) && user.getRole() == UserRole.SECURITY)
                    throw new IllegalArgumentException("권한 불일치");
            if (email != null) userService.userEmailCheck(email);
            SiteUser siteUser = userService.save(name, password, email, aptNumber, role, apt);
            return this.getUserResponseDTO(siteUser);

    }

    @Transactional
    public List<UserResponseDTO> saveUserGroup(int min, int max, Long aptId, String username, int h, int w, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Apt apt = aptService.get(aptId);
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        if (user.getRole() != UserRole.ADMIN)
            if (!user.getApt().equals(apt) && user.getRole() == UserRole.SECURITY)
                throw new IllegalArgumentException("권한 불일치");
        List<UserResponseDTO> userResponseDTOList = new ArrayList<>();

        if (user.getRole() == UserRole.SECURITY || user.getRole() == UserRole.ADMIN) {
            for (int aptNum = min; aptNum <= max; aptNum++) {  // min부터 max까지 각 동에 대해 반복
                for (int i = 1; i <= h; i++) {  // 층수에 대한 반복
                    for (int j = 1; j <= w; j++) {  // 층당 세대수에 대한 반복
                        String jKey = String.valueOf(j);
                        if (j < 10) jKey = "0" + jKey;  // 세대수가 한자리일 때 0을 붙임

                        String name = aptNum + "_" + String.valueOf(i) + jKey;  // 아파트 번호 생성
                        SiteUser _user = userService.saveGroup(name, aptNum, apt);// 사용자 그룹 저장
                        if (aptNum == min && i == 1 && j == 1 || aptNum == max && i == h && j == w){
                            userResponseDTOList.add(
                                    UserResponseDTO.builder()
                                            .username(_user.getUsername())
                                            .aptNum(_user.getAptNum())
                                            .aptResponseDTO(this.getAptResponseDTO(apt))
                                            .build()
                            );
                         }
                    }
                }
            }
            this.userService.save(aptId + "_security", aptId + "_security", aptId + "security@security.co.kr", 0, 1, apt);
            return userResponseDTOList;
        } else {
            throw new IllegalArgumentException("권한 불일치");
        }
    }


    @Transactional
    public Page<UserResponseDTO> getUserList(int page, Long aptId, String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Apt apt = aptService.get(aptId);
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        Pageable pageable = PageRequest.of(page, 10);
        Page<SiteUser> userList = userService.getUserList(pageable, aptId);
        List<UserResponseDTO> responseDTOList = new ArrayList<>();
        if (user.getRole() != UserRole.ADMIN)
            if (!user.getApt().equals(apt) && user.getRole() == UserRole.SECURITY)
                throw new IllegalArgumentException("권한 불일치");
        for (SiteUser siteUser : userList) {

            UserResponseDTO userResponseDTO = getUserResponseDTO(siteUser);
            responseDTOList.add(userResponseDTO);
        }
        return new PageImpl<>(responseDTOList, pageable, userList.getTotalElements());
    }

    @Transactional
    public UserResponseDTO getUserDetail(String userId, String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        SiteUser user1 = userService.getUser(userId);
        if (user1 == null)
            throw new DataNotFoundException("타깃 유저 객체 없음");
        Apt apt = aptService.get(user1.getApt().getId());
        if (user.getRole() != UserRole.ADMIN)
            if (!user.getApt().equals(apt) && user.getRole() == UserRole.SECURITY)
                throw new IllegalArgumentException("권한 불일치");
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        return this.getUserResponseDTO(user1);
    }


    @Transactional
    public UserResponseDTO updateUser(String username, String email) {
        SiteUser user = userService.get(username);
        if (user == null)
            throw new DataNotFoundException("유저 객체 없음");
        if (!user.getUsername().equals(username)) throw new IllegalArgumentException("로그인 유저와 불일치");
        if (email != null) userService.userEmailCheck(email);
        SiteUser siteUser = userService.update(user, email);
        if (siteUser == null)
            throw new DataNotFoundException("수정할 유저 객체 없음");
        Apt apt = aptService.get(siteUser.getApt().getId());
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        return this.getUserResponseDTO(siteUser);
    }

    @Transactional
    public void updatePassword(String username, String password, String newPassword1, String newPassword2) {
        SiteUser user = userService.get(username);
        if (user == null)
            throw new DataNotFoundException("유저 객체 없음");
        if (!user.getUsername().equals(username)) throw new IllegalArgumentException("로그인 유저와 불일치");
        if (!this.userService.isMatch(password, user.getPassword()))
            throw new IllegalArgumentException("기존 비밀번호 일치 X");
        else if (!newPassword1.equals(newPassword2))
            throw new IllegalArgumentException("새 비밀번호 일치 X");
        else if (!this.userService.isMatch(newPassword1, user.getPassword()))
            userService.updatePassword(user, newPassword1);
        else
            throw new IllegalArgumentException("기존 비밀번호와 새 비밀번호가 일치함");
    }

    @Transactional
    public void deleteUser(String username, Long profileId, String deleteUsername) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        SiteUser deleteUser = userService.get(deleteUsername);
        if (deleteUser == null)
            throw new DataNotFoundException("타겟 유저 없음");
        if (user.getRole() != UserRole.ADMIN)
            if (!(user.getRole().equals(UserRole.SECURITY) && user.getApt().equals(deleteUser.getApt())))
                throw new IllegalArgumentException("삭제 권한 없음");
        this.deleteUsers(deleteUser);
    }

    private void deleteUsers(SiteUser user) {
        List<Profile> profileList = profileService.findProfilesByUserList(user);
        for (Profile profile : profileList)
            this.deleteProfiles(user, profile);
        userService.deleteUser(user);
    }

    @Transactional
    public UserResponseDTO getUser(String username) {
        SiteUser user = userService.get(username);
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        return this.getUserResponseDTO(user);
    }


    /**
     * Apt
     */

    private AptResponseDTO getAptResponseDTO(Apt apt) {
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.APT.getKey(apt.getId().toString()));
        List<ImageListResponseDTO> imageListResponseDTOS = new ArrayList<>();
        if (_multiKey.isPresent()) {
            for (String value : _multiKey.get().getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(value);
                _fileSystem.ifPresent(fileSystem -> imageListResponseDTOS.add(ImageListResponseDTO.builder().key(fileSystem.getK()).value(fileSystem.getV()).build()));
            }
        }
        return AptResponseDTO.builder().aptId(apt.getId()).aptName(apt.getAptName()).roadAddress(apt.getRoadAddress()).urlList(imageListResponseDTOS).build();
    }

    @Transactional
    public AptResponseDTO saveApt(String roadAddress, String aptName, String username) {
        SiteUser user = userService.get(username);
        if (user == null)
            throw new DataNotFoundException("유저 객체 없음");
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("권한 불일치");
        Apt apt = aptService.save(roadAddress, aptName);
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        return this.getAptResponseDTO(apt);
    }

    @Transactional
    public AptResponseDTO updateApt(Long profileId, Long aptId, String roadAddress, String aptName, List<String> key, String username) {
        SiteUser user = userService.get(username);
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        Profile profile = profileService.findById(profileId);
        Apt apt = aptService.get(aptId);
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        if (user.getRole() != UserRole.ADMIN && (user.getRole() != UserRole.SECURITY && !user.getApt().equals(apt)))
            throw new IllegalArgumentException("권한 불일치");
        apt = aptService.update(apt, roadAddress, aptName);
        Optional<MultiKey> _newMultiKey = multiKeyService.get(ImageKey.TEMP.getKey(username + "." + profile.getId()));
        Optional<MultiKey> _oldMulti = multiKeyService.get(ImageKey.APT.getKey(apt.getId().toString()));
        if (_oldMulti.isPresent()) if (key != null) {
            for (String k : key) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(k);
                _fileSystem.ifPresent(fileSystem -> {
                    fileSystemService.delete(fileSystem);
                    _oldMulti.get().getVs().remove(key);
                    deleteFile(_fileSystem.get());
                });
            }
        }
        if (_newMultiKey.isPresent()) {
            String newFile = "/api/apt" + "/" + apt.getId() + "/";
            for (String values : _newMultiKey.get().getVs()) {
                Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.APT.getKey(apt.getId().toString()));
                Optional<FileSystem> _newFileSystem = fileSystemService.get(values);
                if (_newFileSystem.isPresent()) {
                    String newUrl = this.fileMove(_newFileSystem.get().getV(), newFile, _newFileSystem.get());
                    if (_multiKey.isPresent()) {
                        multiKeyService.add(_multiKey.get(), ImageKey.APT.getKey(apt.getId().toString() + "." + _multiKey.get().getVs().size()));
                        fileSystemService.save(_multiKey.get().getVs().getLast(), newUrl);
                    } else {
                        MultiKey multiKey = multiKeyService.save(ImageKey.APT.getKey(apt.getId().toString()), ImageKey.APT.getKey(apt.getId().toString() + ".0"));
                        fileSystemService.save(multiKey.getVs().getLast(), newUrl);

                    }
                }
            }
            multiKeyService.delete(_newMultiKey.get());
        }

        return getAptResponseDTO(apt);
    }

    @Transactional
    public List<AptResponseDTO> getAptList(String username) {
        SiteUser user = userService.get(username);
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("권한 불일치");
        List<Apt> aptList = aptService.getAptList();
        List<AptResponseDTO> responseDTOList = new ArrayList<>();
        for (Apt apt : aptList) {
            AptResponseDTO aptResponseDTO = this.getAptResponseDTO(apt);
            responseDTOList.add(aptResponseDTO);
        }
        return responseDTOList;
    }


    @Transactional
    public AptResponseDTO getAptDetail(Long aptId, String username) {
        SiteUser user = userService.get(username);
        Apt apt = aptService.get(aptId);
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        if (user.getRole() != UserRole.ADMIN && (user.getRole() != UserRole.SECURITY && !user.getApt().equals(apt)))
            throw new IllegalArgumentException("권한 불일치");
        return this.getAptResponseDTO(apt);
    }

    /**
     * Image
     */

    @Transactional
    public ImageResponseDTO tempUpload(MultipartFile fileUrl, Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        if (!fileUrl.isEmpty()) {
            try {
                String path = AptProjectApplication.getOsType().getLoc();
                Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.TEMP.getKey(username + "." + profile.getId()));
                if (_fileSystem.isPresent()) {
                    FileSystem fileSystem = _fileSystem.get();
                    File file = new File(path + fileSystem.getV());
                    if (file.exists()) file.delete();
                    fileSystemService.delete(_fileSystem.get());

                }
                UUID uuid = UUID.randomUUID();
                String fileLoc = "/api/user" + "/" + username + "/temp/" + profile.getId() + "/" + uuid + "." + fileUrl.getContentType().split("/")[1];
                FileSystem fileSystem = fileSystemService.save(ImageKey.TEMP.getKey(username + "." + profile.getId()), fileLoc);

                File file = new File(path + fileLoc);
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                fileUrl.transferTo(file);
                return ImageResponseDTO.builder().key(fileSystem.getK()).url(fileSystem.getV()).build();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Transactional
    public ImageResponseDTO tempUploadProfile(MultipartFile fileUrl, String username) {
        SiteUser user = userService.get(username);
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        if (!fileUrl.isEmpty()) {
            try {
                String path = AptProjectApplication.getOsType().getLoc();
                Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.TEMP.getKey(username));
                if (_fileSystem.isPresent()) {
                    FileSystem fileSystem = _fileSystem.get();
                    File file = new File(path + fileSystem.getV());
                    if (file.exists()) file.delete();
                    fileSystemService.delete(_fileSystem.get());
                }
                UUID uuid = UUID.randomUUID();
                String fileLoc = "/api/user" + "/" + username + "/temp/" + uuid + "." + fileUrl.getContentType().split("/")[1];
                FileSystem fileSystem = fileSystemService.save(ImageKey.TEMP.getKey(username), fileLoc);

                File file = new File(path + fileLoc);
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                fileUrl.transferTo(file);
                return ImageResponseDTO.builder().key(fileSystem.getK()).url(fileSystem.getV()).build();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Transactional
    public List<ImageListResponseDTO> tempUploadList(MultipartFile fileUrl, Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        if (!fileUrl.isEmpty()) {
            try {
                String path = AptProjectApplication.getOsType().getLoc();
                UUID uuid = UUID.randomUUID();
                String fileLoc = "/api/user" + "/" + username + "/temp_list/" + profile.getId() + "/" + uuid + "." + fileUrl.getContentType().split("/")[1];
                File file = new File(path + fileLoc);
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                fileUrl.transferTo(file);
                Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(username + "." + profile.getId()));
                if (_multiKey.isEmpty()) {
                    MultiKey multiKey = multiKeyService.save(ImageKey.TEMP.getKey(username + "." + profile.getId()), ImageKey.TEMP.getKey(username + "." + profile.getId()) + ".0");
                    fileSystemService.save(multiKey.getVs().getLast(), fileLoc);
                } else {
                    multiKeyService.add(_multiKey.get(), ImageKey.TEMP.getKey(username + "." + profile.getId()) + "." + _multiKey.get().getVs().size());
                    fileSystemService.save(_multiKey.get().getVs().getLast(), fileLoc);
                }
                Optional<MultiKey> _newMultiKey = multiKeyService.get(ImageKey.TEMP.getKey(username + "." + profile.getId()));
                List<ImageListResponseDTO> imageListResponseDTOS = new ArrayList<>();
                if (_newMultiKey.isPresent()) for (String value : _newMultiKey.get().getVs()) {
                    Optional<FileSystem> fileSystem = fileSystemService.get(value);
                    fileSystem.ifPresent(system -> imageListResponseDTOS.add(ImageListResponseDTO.builder().key(fileSystem.get().getK()).value(fileSystem.get().getV()).build()));
                }
                return imageListResponseDTOS;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Transactional
    private void deleteFile(FileSystem fileSystem) {
        String path = AptProjectApplication.getOsType().getLoc();
        Path tempPath = Paths.get(path + fileSystem.getV());
        File file = tempPath.toFile();
        this.deleteFolder(file.getParentFile());
    }

    @Transactional
    public String fileMove(String url, String newUrl, FileSystem fileSystem) {
        try {
            String path = AptProjectApplication.getOsType().getLoc();
            Path tempPath = Paths.get(path + url);
            Path newPath = Paths.get(path + newUrl + tempPath.getFileName());

            Files.createDirectories(newPath.getParent());
            Files.move(tempPath, newPath);
            File file = tempPath.toFile();
            if (file.getParentFile().list().length == 0) this.deleteFolder(file.getParentFile());
            else file.delete();
            fileSystemService.delete(fileSystem);
            return newUrl + tempPath.getFileName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Transactional
    public void deleteImageList(String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId().toString()));
        String path = AptProjectApplication.getOsType().getLoc();
        if (_multiKey.isPresent()) {
            for (String value : _multiKey.get().getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(value);
                if (_fileSystem.isPresent()) {
                    File file = new File(path + _fileSystem.get().getV());
                    if (file.exists()) {
                        if (file.getParentFile().list().length == 0) this.deleteFolder(file.getParentFile());
                        else file.delete();
                    }
                    fileSystemService.delete(_fileSystem.get());
                }
            }
            multiKeyService.delete(_multiKey.get());
        }
    }


    /**
     * File
     */
    public void deleteFolder(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File list : file.listFiles())
                    deleteFolder(list);
            }
            file.delete();
        }
    }

    /**
     * Profile
     */
    @Transactional
    public ProfileResponseDTO saveProfile(String name, String url, String username) {
        SiteUser user = userService.get(username);
        List<Profile> profileList = profileService.findProfilesByUserList(user);
        for (Profile profile : profileList) {
            if (profile.getName().equals(name)) {
                throw new DataDuplicateException("중복된 프로필 이름");
            }
        }
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        if (!name.trim().isEmpty()) {
            Profile profile = profileService.save(user, name);
            Optional<FileSystem> _newFileSystem = fileSystemService.get(ImageKey.TEMP.getKey(username));
            String newFile = "/api/user" + "/" + username + "/profile" + "/" + profile.getId() + "/";
            if (_newFileSystem.isPresent()) {
                String newUrl = this.fileMove(_newFileSystem.get().getV(), newFile, _newFileSystem.get());
                FileSystem fileSystem = fileSystemService.save(ImageKey.USER.getKey(username + "." + profile.getId()), newUrl);
                url = fileSystem.getV();
            }
            return ProfileResponseDTO.builder().id(profile.getId()).url(url).name(profile.getName()).username(profile.getUser().getUsername()).build();
        }
        return null;
    }


    private ProfileResponseDTO profileResponseDTO(Profile profile) {
        Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.USER.getKey(profile.getUser().getUsername() + "." + profile.getId()));
        String url = null;
        if (_fileSystem.isPresent()) url = _fileSystem.get().getV();
        return ProfileResponseDTO.builder().id(profile.getId()).url(url).name(profile.getName()).username(profile.getUser().getUsername()).build();
    }

    @Transactional
    public ProfileResponseDTO getProfile(Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);

        return profileResponseDTO(profile);

    }

    @Transactional
    public List<ProfileResponseDTO> getProfileList(String username) {
        SiteUser user = userService.get(username);
        if (user == null) throw new DataNotFoundException("유저 객체 없음");
        List<ProfileResponseDTO> responseDTOList = new ArrayList<>();
        List<Profile> profileList = profileService.findProfilesByUserList(user);
        if (profileList == null) throw new DataNotFoundException("프로필 리스트 객체 없음");
        for (Profile profile : profileList) {
            Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.USER.getKey(user.getUsername() + "." + profile.getId()));
            String url = null;
            if (_fileSystem.isPresent()) url = _fileSystem.get().getV();
            responseDTOList.add(ProfileResponseDTO.builder().id(profile.getId()).url(url).username(profile.getUser().getUsername()).name(profile.getName()).build());
        }
        return responseDTOList;
    }

    @Transactional
    public ProfileResponseDTO updateProfile(String username, String url, String name, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        profileService.updateProfile(profile, name);
        Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.USER.getKey(user.getUsername() + "." + profile.getId()));
        String path = AptProjectApplication.getOsType().getLoc();
        if (_fileSystem.isPresent() && (url == null || !_fileSystem.get().getV().equals(url))) {
            File old = new File(path + _fileSystem.get().getV());
            if (old.exists()) old.delete();
        }
        if (url != null && !url.isBlank()) {
            String newFile = "/api/user" + "/" + username + "/profile" + "/" + profile.getId() + "/";
            Optional<FileSystem> _newFileSystem = fileSystemService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId()));
            if (_newFileSystem.isPresent()) {
                String newUrl = this.fileMove(_newFileSystem.get().getV(), newFile, _newFileSystem.get());
                fileSystemService.save(ImageKey.USER.getKey(user.getUsername() + "." + profile.getId()), newUrl);
            }
        }
        Optional<FileSystem> _newUserFileSystem = fileSystemService.get(ImageKey.USER.getKey(user.getUsername() + "." + profile.getId()));
        if (_newUserFileSystem.isPresent())
            url = _newUserFileSystem.get().getV();
        return ProfileResponseDTO.builder().name(profile.getName()).username(user.getUsername()).url(url).id(profile.getId()).build();
    }

    private String profileUrl(String username, Long id) {
        Optional<FileSystem> _profileFileSystem = fileSystemService.get(ImageKey.USER.getKey(username + "." + id));
        String profileUrl = null;
        if (_profileFileSystem.isPresent()) profileUrl = _profileFileSystem.get().getV();
        return profileUrl;
    }

    @Transactional
    public void deleteProfile(String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);

        this.deleteProfiles(user, profile);
    }

    private void deleteProfiles(SiteUser user, Profile profile) {
        List<Article> articleList = articleService.findByArticle(profile.getId());
        for (Article article : articleList)
            this.deleteArticle(article.getId());
        List<Comment> commentList = commentService.findByProfile(profile.getId());
        for (Comment comment : commentList)
            this.deleteComment(user.getUsername(), profile.getId(), comment.getId());
        List<LessonUser> lessonUserList = lessonUserService.getMyList(profile);
        for (LessonUser lessonUser : lessonUserList)
            lessonUserService.delete(lessonUser);
        List<Lesson> lessonList = lessonService.findByProfile(profile.getId());
        for (Lesson lesson : lessonList)
            this.deleteLessonList(lesson);
        Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.USER.getKey(user.getUsername() + "." + profile.getId()));
        if (_fileSystem.isPresent()) {
            this.deleteFile(_fileSystem.get());
            fileSystemService.delete(_fileSystem.get());
        }

        Optional<FileSystem> _fileProfileTemp = fileSystemService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId()));
        if (_fileProfileTemp.isPresent()) {
            this.deleteFile(_fileProfileTemp.get());
            fileSystemService.delete(_fileProfileTemp.get());
        }
        this.deleteImageList(user.getUsername(), profile.getId());
        profileService.deleteProfile(profile);
    }

    /**
     * Category
     */

    @Transactional
    public CategoryResponseDTO saveCategory(String username, String name, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("권한 불일치");
        Category category = this.categoryService.save(name);
        return categoryResponseDTO(category);

    }

    @Transactional
    public void deleteCategory(Long categoryId, String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("권한 불일치");
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new DataNotFoundException("카테고리 객체 없음");

        categoryService.delete(category);
    }

    @Transactional
    public CategoryResponseDTO getCategory(Long categoryId, String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new DataNotFoundException("카테고리 객체 없음");

        return categoryResponseDTO(category);
    }

    @Transactional
    public List<CategoryResponseDTO> getCategoryList(String username, Long profileId) {
        SiteUser siteUser = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(siteUser, profile);
        List<Category> categoryList = categoryService.getList();
        List<CategoryResponseDTO> responseDTOList = new ArrayList<>();
        for (Category category : categoryList) {
            responseDTOList.add(categoryResponseDTO(category));
        }
        return responseDTOList;

    }

    @Transactional
    public CategoryResponseDTO updateCategory(String username, Long profileId, Long id, String name) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Category category = categoryService.findById(id);
        if (category == null) throw new DataNotFoundException("카테고리 객체 없음");
        if (user.getRole() != UserRole.ADMIN) throw new IllegalArgumentException("권한 불일치");
        category = categoryService.update(category, name);

        return categoryResponseDTO(category);
    }

    private CategoryResponseDTO categoryResponseDTO(Category category) {
        return CategoryResponseDTO.builder().id(category.getId()).name(category.getName()).modifyDate(this.dateTimeTransfer(category.getModifyDate())).createDate(this.dateTimeTransfer(category.getCreateDate())).build();
    }


    /**
     * Article
     */
    @Transactional
    public ArticleResponseDTO saveArticle(Long profileId, Long categoryId, List<String> tagName, String title, String content, String username, Boolean topActive) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new DataNotFoundException("카테고리 객체 없음");
        Article article = articleService.save(profile, title, content, category, topActive);
        List<TagResponseDTO> tagResponseDTOList = new ArrayList<>();
        if (tagName != null) {
            for (String name : tagName) {
                Tag tag = tagService.findByName(name);
                if (tag == null) tag = tagService.save(name);
                articleTagService.save(article, tag);
                tagResponseDTOList.add(tagResponseDTO(tag));
            }
        }
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId().toString()));
        _multiKey.ifPresent(multiKey -> this.updateArticleContent(article, multiKey));
        return this.getArticleResponseDTO(article, tagResponseDTOList);
    }


    @Transactional
    public ArticleResponseDTO updateArticle(Long profileId, Long articleId, Long categoryId, List<String> tagName, String title, List<Long> articleTagId, String content, String username, Boolean topActive) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Category category = categoryService.findById(categoryId);
        if (category == null) throw new DataNotFoundException("카테고리 객체 없음");
        Article targetArticle = articleService.findById(articleId);
        if (profile != targetArticle.getProfile()) throw new IllegalArgumentException("수정 권한 없음");
        Article article = articleService.update(targetArticle, title, content, category, topActive);
        List<TagResponseDTO> tagResponseDTOList = new ArrayList<>();
        if (articleTagId != null) {
            for (Long id : articleTagId) {
                ArticleTag articleTag = articleTagService.findById(id);
                if (articleTag == null)
                    throw new DataNotFoundException("게시물 태그 객체가 없음");
                List<ArticleTag> articleTags = articleTagService.findByTagList(articleTag.getTag().getId());
                if (articleTags.size() == 1L) {
                    for (ArticleTag articleTag1 : articleTags) {
                        Tag tag = tagService.findById(articleTag1.getTag().getId());
                        tagService.delete(tag);
                    }
                }
                articleTagService.delete(articleTag);
            }
        }
        if (tagName != null)
            for (String name : tagName) {
                Tag tag = tagService.findByName(name);

                if (tag == null) tag = tagService.save(name);
                ArticleTag articleTag = articleTagService.findByTagId(tag.getId());
                if (articleTag == null)
                    articleTagService.save(article, tag);
                tagResponseDTOList.add(tagResponseDTO(tag));
            }
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId().toString()));
        _multiKey.ifPresent(multiKey -> this.updateArticleContent(article, multiKey));

        return this.getArticleResponseDTO(article, tagResponseDTOList);
    }

    @Transactional
    public ArticleResponseDTO articleDetail(Long articleId, Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (article == null) throw new DataNotFoundException("게시물 객체 없음");
        List<ArticleTag> articleTagList = articleTagService.getArticle(article.getId());
        if (articleTagList == null) throw new DataNotFoundException("게시물태그 객체 없음");
        List<TagResponseDTO> responseDTOList = new ArrayList<>();
        for (ArticleTag articleTag : articleTagList) {
            Tag tag = tagService.findById(articleTag.getTag().getId());
            responseDTOList.add(tagResponseDTO(tag));
        }
        return this.getArticleResponseDTO(article, responseDTOList);
    }


    @Transactional
    public List<ArticleResponseDTO> topActive(String username, Long aptId, Long profileId, Long categoryId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Boolean topActive = true;
        List<Article> articleList;
        if (user.getRole() == UserRole.ADMIN) {
            articleList = articleService.topActive(aptId, categoryId, topActive);
        } else {
            articleList = articleService.topActive(user.getApt().getId(), categoryId, topActive);
        }
        List<ArticleResponseDTO> articleResponseDTOList = new ArrayList<>();
        for (Article article : articleList) {
            ArticleResponseDTO articleResponseDTO = ArticleResponseDTO.builder()
                    .articleId(article.getId())
                    .topActive(article.getTopActive())
                    .title(article.getTitle())
                    .content(article.getContent())
                    .categoryName(article.getCategory().getName())
                    .createDate(this.dateTimeTransfer(article.getCreateDate()))
                    .modifyDate(this.dateTimeTransfer(article.getModifyDate()))
                    .build();
            articleResponseDTOList.add(articleResponseDTO);
        }
        return articleResponseDTOList;
    }

    @Transactional
    public Page<ArticleResponseDTO> articleList(String username, Long aptId, int page, Long profileId, Long categoryId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Pageable pageable = PageRequest.of(page, 15);
        Boolean topActive = false;
        Page<Article> articleList;
        if (user.getRole() == UserRole.ADMIN) {
            articleList = articleService.getArticleList(pageable, aptId, categoryId, topActive);
        } else {
            articleList = articleService.getArticleList(pageable, user.getApt().getId(), categoryId, topActive);
        }
        List<ArticleResponseDTO> articleResponseDTOList = new ArrayList<>();
        for (Article article : articleList) {
            List<ArticleTag> articleTagList = articleTagService.getArticle(article.getId());
            if (articleTagList == null) throw new DataNotFoundException("게시물태그 객체 없음");
            List<TagResponseDTO> responseDTOList = new ArrayList<>();
            for (ArticleTag articleTag : articleTagList) {
                Tag tag = tagService.findById(articleTag.getTag().getId());
                responseDTOList.add(tagResponseDTO(tag));
            }
            ArticleResponseDTO articleResponseDTO = this.getArticleResponseDTO(article, responseDTOList);
            articleResponseDTOList.add(articleResponseDTO);
        }
        return new PageImpl<>(articleResponseDTOList, pageable, articleList.getTotalElements());
    }

    @Transactional
    public void deleteArticle(String username, Long profileId, Long articleId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (!article.getProfile().equals(profile)) {
            throw new IllegalArgumentException("작성자의 프로필이 일치하지 않습니다");
        }

        this.deleteArticle(article.getId());
    }

    @Transactional
    private void deleteArticle(Long articleId) {
        Article article = articleService.findById(articleId);
        if (article == null)
            throw new DataNotFoundException("게시물 객체 없음");
        List<Comment> commentList = commentService.findByArticle(article.getId());
        for (Comment comment : commentList)
            deleteChildren(comment);
        List<Love> loveList = loveService.findByArticle(article.getId());
        for (Love love : loveList)
            loveService.delete(love);
        List<ArticleTag> articleTagList = articleTagService.getArticle(article.getId());
        for (ArticleTag articleTag : articleTagList) {
            List<ArticleTag> articleTags = articleTagService.findByTagList(articleTag.getTag().getId());
            if (articleTags.size() == 1L) {
                for (ArticleTag articleTag1 : articleTags) {
                    Tag tag = tagService.findById(articleTag1.getTag().getId());
                    tagService.delete(tag);
                }
            }
            articleTagService.delete(articleTag);
        }
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.ARTICLE.getKey(article.getId().toString()));
        String path = AptProjectApplication.getOsType().getLoc();
        if (_multiKey.isPresent()) {
            for (String values : _multiKey.get().getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(values);
                if (_fileSystem.isPresent()) {
                    Path filePath = Paths.get(path + _fileSystem.get().getV());
                    File file = filePath.toFile();
                    this.deleteFolder(file.getParentFile());
                    fileSystemService.delete(_fileSystem.get());
                }
            }
            multiKeyService.delete(_multiKey.get());
        }
        articleService.deleteArticle(article);
    }

    @Transactional
    public Page<ArticleResponseDTO> searchArticle(String username, Long profileId, int page, String keyword, int sort, Long categoryId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Sorts sorts = Sorts.values()[sort];
        Pageable pageable = PageRequest.of(page, 15);
        Page<Article> searchArticleList = null;
        if (categoryId == null)
            searchArticleList = articleService.searchByKeyword(user.getApt().getId(), pageable, keyword, sorts);
        else
            searchArticleList = articleService.searchByCategoryKeyword(user.getApt().getId(), pageable, keyword, sorts, categoryId);
        if (searchArticleList.isEmpty())
            throw new DataNotFoundException("검색 결과가 없습니다");
        List<ArticleResponseDTO> articleResponseDTOList = new ArrayList<>();
        for (Article article : searchArticleList) {
            List<ArticleTag> articleTagList = articleTagService.getArticle(article.getId());
            if (articleTagList == null) throw new DataNotFoundException("게시물태그 객체 없음");
            List<TagResponseDTO> responseDTOList = new ArrayList<>();
            for (ArticleTag articleTag : articleTagList) {
                Tag tag = tagService.findById(articleTag.getTag().getId());
                responseDTOList.add(tagResponseDTO(tag));
            }
            ArticleResponseDTO articleResponseDTO = this.getArticleResponseDTO(article, responseDTOList);
            articleResponseDTOList.add(articleResponseDTO);
        }
        return new PageImpl<>(articleResponseDTOList, pageable, searchArticleList.getTotalElements());
    }

    private ArticleResponseDTO getArticleResponseDTO(Article article, List<TagResponseDTO> responseDTOList) {
        String profileUrl = this.profileUrl(article.getProfile().getUser().getUsername(), article.getProfile().getId());
        Optional<MultiKey> _multiKey = multiKeyService.get(article.getId().toString());
        List<String> urlList = new ArrayList<>();
        if (_multiKey.isPresent()) {
            for (String keyName : _multiKey.get().getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(keyName);
                _fileSystem.ifPresent(fileSystem -> urlList.add(fileSystem.getV()));
            }
        }

        return ArticleResponseDTO.builder()//
                .articleId(article.getId()).title(article.getTitle()).content(article.getContent()).createDate(this.dateTimeTransfer(article.getCreateDate())).modifyDate(this.dateTimeTransfer(article.getModifyDate())).categoryName(article.getCategory().getName()).profileResponseDTO(ProfileResponseDTO.builder().id(article.getProfile().getId()).username(article.getProfile().getUser().getUsername()).url(profileUrl).name(article.getProfile().getName()).build()).tagResponseDTOList(responseDTOList).topActive(article.getTopActive()).urlList(urlList).build();
    }

    private void updateArticleContent(Article article, MultiKey multiKey) {
        String content = article.getContent();
        for (String keyName : multiKey.getVs()) {
            Optional<MultiKey> _articleMulti = multiKeyService.get(ImageKey.ARTICLE.getKey(article.getId().toString()));
            Optional<FileSystem> _fileSystem = fileSystemService.get(keyName);
            if (_fileSystem.isPresent()) {
                String newFile = "/api/article" + "/" + article.getId() + "/";
                String newUrl = this.fileMove(_fileSystem.get().getV(), newFile, _fileSystem.get());
                if (_articleMulti.isEmpty()) {
                    MultiKey multiKey1 = multiKeyService.save(ImageKey.ARTICLE.getKey(article.getId().toString()), ImageKey.ARTICLE.getKey(article.getId().toString() + ".0"));
                    fileSystemService.save(multiKey1.getVs().getLast(), newUrl);
                } else {
                    multiKeyService.add(_articleMulti.get(), ImageKey.ARTICLE.getKey(article.getId().toString()) + "." + _articleMulti.get().getVs().size());
                    fileSystemService.save(_articleMulti.get().getVs().getLast(), newUrl);
                }
                content = content.replace(_fileSystem.get().getV(), newUrl);
            }
        }
        multiKeyService.delete(multiKey);
        articleService.updateContent(article, content);
    }

    /**
     * Comment
     */

    @Transactional
    private CommentResponseDTO commentResponseDTO(Comment comment, Profile profile) {
        return CommentResponseDTO.builder() //
                .id(comment.getId()) //
                .content(comment.getContent()) //
                .articleId(comment.getArticle().getId()) //
                .profileResponseDTO(ProfileResponseDTO.builder() //
                        .id(profile.getId()) //
                        .name(profile.getName()) //
                        .url(profileUrl(profile.getName(), profile.getId())) //
                        .username(profile.getUser().getUsername()) //
                        .build()) //
                .createDate(this.dateTimeTransfer(comment.getCreateDate())) //
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null) //
                .build();
    }

    @Transactional
    private CommentResponseDTO commentResponseDTOList(Comment comment, Profile profile, List<CommentResponseDTO> commentResponseDTOList) {
        return CommentResponseDTO.builder() //
                .id(comment.getId()) //
                .content(comment.getContent()) //
                .articleId(comment.getArticle().getId()) //
                .profileResponseDTO(ProfileResponseDTO.builder() //
                        .id(profile.getId()) //
                        .name(profile.getName()) //
                        .url(profileUrl(profile.getName(), profile.getId())) //
                        .username(profile.getUser().getUsername()) //
                        .build()) //
                .createDate(this.dateTimeTransfer(comment.getCreateDate())) //
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null) //
                .commentResponseDTOList(commentResponseDTOList)
                .build();
    }

    @Transactional
    public CommentResponseDTO saveComment(String username, Long articleId, Long parentId, Long profileId, String content) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (article == null) throw new DataNotFoundException("게시물 객체 없음");
        Comment comment = commentService.saveComment(article, profile, content, parentId);
        if (comment.getParent() != null)
            if (!comment.getParent().getArticle().getId().equals(article.getId()))
                throw new DataNotFoundException("부모 댓글의 게시글 객체와 해당 게시글 객체가 다름");
        return this.commentResponseDTO(comment, profile);
    }

    @Transactional
    public CommentResponseDTO updateComment(String username, Long profileId, Long commentId, String content) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Comment comment = commentService.updateComment(commentId, content);
        if (!profile.equals(comment.getProfile())) throw new IllegalArgumentException("작성자의 프로필이 일치하지 않습니다");
        return this.commentResponseDTO(comment, profile);
    }

    @Transactional
    public Page<CommentResponseDTO> commentList(String username, Long profileId, int page, Long articleId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (article == null)
            throw new DataNotFoundException("게시물 객체 없음");
        Pageable pageable = PageRequest.of(page, 10);
        Page<Comment> commentList = commentService.getCommentPaging(pageable, article.getId());
        if (commentList == null) throw new DataNotFoundException("댓글 객체 없음");
        List<CommentResponseDTO> commentResponseDTOList = new ArrayList<>();
        for (Comment comment : commentList) {
            commentResponseDTOList.add(this.commentList(comment));
        }
        return new PageImpl<>(commentResponseDTOList, pageable, commentList.getTotalElements());
    }

    @Transactional
    public void deleteComment(String username, Long profileId, Long commentId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Comment comment = commentService.findByComment(commentId);
        if (comment == null) throw new DataNotFoundException("댓글 객체 없음");
        List<Comment> commentList = commentService.findByChildrentList(commentId);
        for (Comment comment1 : commentList) {
            deleteChildren(comment1);
        }
        if (!profile.equals(comment.getProfile())) {
            throw new IllegalArgumentException("작성자의 프로필이 일치하지 않습니다");
        }
        commentService.deleteComment(comment);
    }

    private void deleteChildren(Comment comment) {
        List<Comment> commentList = commentService.findByChildrentList(comment.getId());
        if (commentList != null) {
            for (Comment children : commentList) {
                this.deleteChildren(children);
            }
        }
        commentService.deleteComment(comment);
    }

    private CommentResponseDTO commentList(Comment comment) {
        List<Comment> commentList = commentService.findByChildrentList(comment.getId());
        List<CommentResponseDTO> commentResponseDTOList = new ArrayList<>();
        if (commentList != null)
            for (Comment comment1 : commentList) {
                commentResponseDTOList.add(this.commentList(comment1));
            }
        return this.commentResponseDTOList(comment, comment.getProfile(), commentResponseDTOList);

    }

    /**
     * Love
     */
    @Transactional
    public LoveResponseDTO toggleLove(String username, Long articleId, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (article == null) throw new DataNotFoundException("게시물 객체 없음");

        boolean isLoved = loveService.toggleLove(article, profile);
        int count = loveService.countLoveByArticle(article.getId());

        return LoveResponseDTO.builder()
                .count(count)
                .isLoved(isLoved)
                .build();
    }

    @Transactional
    public LoveResponseDTO getLoveInfo(Long articleId, Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Article article = articleService.findById(articleId);
        if (article == null)
            throw new DataNotFoundException("게시물 객체 없음");

        boolean isLoved = loveService.existsByArticleAndProfile(article, profile);
        int count = loveService.countLoveByArticle(article.getId());

        return LoveResponseDTO.builder()
                .count(count)
                .isLoved(isLoved)
                .build();
    }

//    @Transactional
//    public void saveLove(String username, Long articleId, Long profileId) {
//        SiteUser user = userService.get(username);
//        Profile profile = profileService.findById(profileId);
//        this.userCheck(user, profile);
//        Article article = articleService.findById(articleId);
//        if (article == null) throw new DataNotFoundException("게시물 객체 없음");
//        Love love = loveService.findByArticleAndProfile(article, profile);
//        if (love == null)
//            loveService.save(article, profile);
//
//    }
//
//    @Transactional
//    public void deleteLove(String username, Long articleId, Long profileId) {
//        SiteUser user = userService.get(username);
//        Profile profile = profileService.findById(profileId);
//        this.userCheck(user, profile);
//        Article article = articleService.findById(articleId);
//        if (article == null) throw new DataNotFoundException("게시물 객체 없음");
//        Love love = loveService.findByArticleAndProfile(article, profile);
//        if (love == null)
//            throw new DataNotFoundException("게시물 좋아요 객체 없음");
//        if (love.getProfile() != profile)
//            throw new IllegalArgumentException("권한 없음");
//        loveService.delete(love);
//    }
//
//    @Transactional
//    public LoveResponseDTO countLove(Long articleId, Long profileId, String username) {
//        SiteUser user = userService.get(username);
//        Profile profile = profileService.findById(profileId);
//        this.userCheck(user, profile);
//        Article article = articleService.findById(articleId);
//        if (article == null)
//            throw new DataNotFoundException("게시물 객체 없음");
//        List<Love> countLove = loveService.findByArticle(article.getId());
//        int count = countLove.size();
//        return LoveResponseDTO.builder()
//                .count(count).build();
//    }


    /**
     * Tag
     */


    @Transactional
    public TagResponseDTO saveTag(String name, Long profileId, String username) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Tag tag = tagService.findByName(name);
        if (tag == null) tag = tagService.save(name);
        return this.tagResponseDTO(tag);
    }

    @Transactional
    public TagResponseDTO getTag(String username, Long profileId, Long tagId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Tag tag = tagService.findById(tagId);
        if (tag == null) throw new DataNotFoundException("태그 객체 없음");
        return this.tagResponseDTO(tag);
    }

    private TagResponseDTO tagResponseDTO(Tag tag) {
        return TagResponseDTO.builder().id(tag.getId()).name(tag.getName()).build();
    }


    /**
     * function
     */

    private Long dateTimeTransfer(LocalDateTime dateTime) {

        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Center
     */
    @Transactional
    public CenterResponseDTO saveCenter(String username, Long profileId, int type, LocalDateTime endDate, LocalDateTime startDate) {
        SiteUser user = userService.get(username);
        if (user.getRole() == UserRole.USER)
            throw new IllegalArgumentException("권한 불일치");
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Apt apt = aptService.get(user.getApt().getId());
        if (apt == null) throw new DataNotFoundException("아파트 객체 없음");
        CultureCenter cultureCenter = cultureCenterService.save(type, endDate, startDate, apt);

        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId()));
        if (_multiKey.isPresent()) {
            for (String values : _multiKey.get().getVs()) {
                Optional<MultiKey> _centerMultiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
                Optional<FileSystem> _fileSystem = fileSystemService.get(values);
                if (_fileSystem.isPresent()) {
                    String newFile = "/api/center" + "/" + cultureCenter.getId() + "/";
                    String newUrl = this.fileMove(_fileSystem.get().getV(), newFile, _fileSystem.get());
                    if (_centerMultiKey.isPresent()) {
                        MultiKey multiKey = multiKeyService.add(_centerMultiKey.get(), ImageKey.CENTER.getKey(cultureCenter.getId().toString() + "." + _centerMultiKey.get().getVs().size()));
                        fileSystemService.save(multiKey.getVs().getLast(), newUrl);
                    } else {
                        MultiKey multiKey = multiKeyService.save(ImageKey.CENTER.getKey(cultureCenter.getId().toString()), ImageKey.CENTER.getKey(cultureCenter.getId().toString() + ".0"));
                        fileSystemService.save(multiKey.getVs().getLast(), newUrl);
                    }
                }

            }
            multiKeyService.delete(_multiKey.get());
        }
        Optional<MultiKey> _newMultiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
        MultiKey multiKey = null;
        if (_newMultiKey.isPresent()) multiKey = _newMultiKey.get();
        return this.centerResponseDTO(cultureCenter, multiKey);
    }

    private CenterResponseDTO centerResponseDTO(CultureCenter cultureCenter, MultiKey multiKey) {
        List<ImageListResponseDTO> imageListResponseDTOS = new ArrayList<>();
        if (multiKey != null) {
            for (String value : multiKey.getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(value);
                _fileSystem.ifPresent(fileSystem -> imageListResponseDTOS.add(ImageListResponseDTO.builder().key(fileSystem.getK()).value(fileSystem.getV()).build()));
            }
        }
        return CenterResponseDTO.builder().id(cultureCenter.getId()).startDate(this.dateTimeTransfer(cultureCenter.getOpenTime())).endDate(this.dateTimeTransfer(cultureCenter.getCloseTime())).type(cultureCenter.getCenterType().toString()).createDate(this.dateTimeTransfer(cultureCenter.getCreateDate())).modifyDate(this.dateTimeTransfer(cultureCenter.getModifyDate())).imageListResponseDTOS(imageListResponseDTOS).aptResponseDTO(getAptResponseDTO(cultureCenter.getApt())).build();
    }

    @Transactional
    public CenterResponseDTO getCenter(String username, Long profileId, Long centerId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체 없음");
        if (!cultureCenter.getApt().equals(user.getApt()) && UserRole.ADMIN != user.getRole())
            throw new IllegalArgumentException("권한이 없음");
        Optional<MultiKey> _newMultiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
        MultiKey newMultiKey = null;
        if (_newMultiKey.isPresent())
            newMultiKey = _newMultiKey.get();
        return centerResponseDTO(cultureCenter, newMultiKey);
    }

    @Transactional
    public CenterResponseDTO updateCenter(String username, Long profileId, Long id, int type, LocalDateTime endDate, LocalDateTime startDate) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(id);
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체 없음");
        if (user.getRole() == UserRole.USER && !user.getApt().equals(cultureCenter.getApt()))
            throw new IllegalArgumentException("권한 불일치");
        cultureCenterService.update(cultureCenter, type, endDate, startDate);
        Optional<MultiKey> _newMultiKey = multiKeyService.get(ImageKey.TEMP.getKey(username + "." + profile.getId()));

        if (_newMultiKey.isPresent()) {
            String newFile = "/api/center" + "/" + cultureCenter.getId() + "/";
            for (String values : _newMultiKey.get().getVs()) {
                Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
                Optional<FileSystem> _newFileSystem = fileSystemService.get(values);
                if (_newFileSystem.isPresent()) {
                    String newUrl = this.fileMove(_newFileSystem.get().getV(), newFile, _newFileSystem.get());
                    if (_multiKey.isPresent()) {
                        multiKeyService.add(_multiKey.get(), ImageKey.CENTER.getKey(cultureCenter.getId().toString() + "." + _multiKey.get().getVs().size()));
                        fileSystemService.save(_multiKey.get().getVs().getLast(), newUrl);
                    } else {
                        MultiKey multiKey = multiKeyService.save(ImageKey.CENTER.getKey(cultureCenter.getId().toString()), ImageKey.CENTER.getKey(cultureCenter.getId().toString() + ".0"));
                        fileSystemService.save(multiKey.getVs().getLast(), newUrl);

                    }
                }
            }
            multiKeyService.delete(_newMultiKey.get());
        }
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
        MultiKey newMultiKey = null;
        if (_multiKey.isPresent())
            newMultiKey = _multiKey.get();
        return centerResponseDTO(cultureCenter, newMultiKey);

    }


    @Transactional
    public void deleteCenter(String username, Long profileId, Long centerId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체 없음");
        if (user.getRole() == UserRole.USER && !user.getApt().equals(cultureCenter.getApt()))
            throw new IllegalArgumentException("권한 불일치");
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
        String path = AptProjectApplication.getOsType().getLoc();
        if (_multiKey.isPresent()) {
            for (String values : _multiKey.get().getVs()) {
                Optional<FileSystem> _fileSystem = fileSystemService.get(values);
                if (_fileSystem.isPresent()) {
                    Path tempPath = Paths.get(path + _fileSystem.get().getV());
                    File file = tempPath.toFile();
                    this.deleteFolder(file.getParentFile());
                    fileSystemService.delete(_fileSystem.get());
                }
            }
            multiKeyService.delete(_multiKey.get());
        }
        cultureCenterService.delete(cultureCenter);
    }

    @Transactional
    public List<CenterResponseDTO> getCenterList(String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        List<CultureCenter> cultureCenterList = cultureCenterService.getList(user.getApt().getId());
        if (cultureCenterList == null) throw new DataNotFoundException("센터 리스트 없음");
        List<CenterResponseDTO> centerResponseDTOS = new ArrayList<>();

        for (CultureCenter cultureCenter : cultureCenterList) {
            Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.CENTER.getKey(cultureCenter.getId().toString()));
            MultiKey multiKey = null;
            if (_multiKey.isPresent()) multiKey = _multiKey.get();

            centerResponseDTOS.add(centerResponseDTO(cultureCenter, multiKey));
        }
        return centerResponseDTOS;
    }

    /**
     * Lesson
     */

    @Transactional
    public LessonResponseDTO saveLesson(String username, Long profileId, Long centerId, String name, String content, LocalDateTime startDate, LocalDateTime endDate) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (user.getRole() != UserRole.STAFF && !user.getApt().equals(cultureCenter.getApt()))
            throw new IllegalArgumentException("권한 불일치");
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체가 없음");
        Lesson lesson = lessonService.save(cultureCenter, profile, name, content, startDate, endDate);
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId()));
        if (_multiKey.isPresent())
            lesson = this.updateLessonContent(lesson, _multiKey.get());
        return this.lessonResponseDTO(lesson);
    }

    private Lesson updateLessonContent(Lesson lesson, MultiKey multiKey) {
        String content = lesson.getContent();
        for (String keyName : multiKey.getVs()) {
            Optional<MultiKey> _articleMulti = multiKeyService.get(ImageKey.LESSON.getKey(lesson.getId().toString()));
            Optional<FileSystem> _fileSystem = fileSystemService.get(keyName);
            if (_fileSystem.isPresent()) {
                String newFile = "/api/lesson" + "/" + lesson.getId() + "/";
                String newUrl = this.fileMove(_fileSystem.get().getV(), newFile, _fileSystem.get());
                if (_articleMulti.isEmpty()) {
                    MultiKey multiKey1 = multiKeyService.save(ImageKey.LESSON.getKey(lesson.getId().toString()), ImageKey.LESSON.getKey(lesson.getId().toString() + ".0"));
                    fileSystemService.save(multiKey1.getVs().getLast(), newUrl);
                } else {
                    multiKeyService.add(_articleMulti.get(), ImageKey.LESSON.getKey(lesson.getId().toString()) + "." + _articleMulti.get().getVs().size());
                    fileSystemService.save(_articleMulti.get().getVs().getLast(), newUrl);
                }
                content = content.replace(_fileSystem.get().getV(), newUrl);
            }
        }
        multiKeyService.delete(multiKey);
        return lessonService.updateContent(lesson, content);
    }


    private LessonResponseDTO lessonResponseDTO(Lesson lesson) {
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.CENTER.getKey(lesson.getCultureCenter().getId().toString()));
        MultiKey centerMulti = null;
        if (_multiKey.isPresent()) centerMulti = _multiKey.get();
        Optional<FileSystem> _fileSystem = fileSystemService.get(ImageKey.USER.getKey(lesson.getProfile().getUser().getUsername() + "." + lesson.getProfile().getId()));
        String profileUrl = null;
        if (_fileSystem.isPresent()) profileUrl = _fileSystem.get().getV();
        Optional<MultiKey> _lessonKey = multiKeyService.get(ImageKey.CENTER.getKey(lesson.getId().toString()));
        List<String> key = new ArrayList<>();
        if (_lessonKey.isPresent()) {
            for (String keyName : _lessonKey.get().getVs()) {
                Optional<FileSystem> _lessonFileUrl = fileSystemService.get(keyName);
                _lessonFileUrl.ifPresent(fileSystem -> key.add(fileSystem.getK()));

            }
        }

        return LessonResponseDTO.builder() //
                .id(lesson.getId()) //
                .centerResponseDTO(this.centerResponseDTO(lesson.getCultureCenter(), centerMulti)) //
                .profileResponseDTO(ProfileResponseDTO.builder() //
                        .id(lesson.getProfile().getId()) //
                        .username(lesson.getProfile().getUser().getUsername()) //
                        .name(lesson.getProfile().getName()) //
                        .url(profileUrl) //
                        .build()) //
                .createDate(this.dateTimeTransfer(lesson.getCreateDate())) //
                .modifyDate(this.dateTimeTransfer(lesson.getModifyDate())) //
                .name(lesson.getName()) //
                .content(lesson.getContent()) //
                .startDate(this.dateTimeTransfer(lesson.getStartDate())) //
                .endDate(this.dateTimeTransfer(lesson.getEndDate())) //
                .build();
    }

    @Transactional
    public LessonResponseDTO getLesson(String username, Long profileId, Long lessonId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Lesson lesson = lessonService.findById(lessonId);
        if (lesson == null)
            throw new DataNotFoundException("레슨 객체 없음");

        if (!user.getApt().equals(lesson.getCultureCenter().getApt()))
            throw new IllegalArgumentException("권한 없음");
        return this.lessonResponseDTO(lesson);
    }

    @Transactional
    public Page<LessonResponseDTO> getLessonPage(String username, Long profileId, int page, Long centerId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Pageable pageable = PageRequest.of(page, 15);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체 없음");
        if (!user.getApt().equals(cultureCenter.getApt()))
            throw new IllegalArgumentException("권한 없음");

        Page<Lesson> lessonPage = lessonService.getPage(user.getApt().getId(), pageable, cultureCenter);
        if (lessonPage == null)
            throw new DataNotFoundException("레슨 페이지 객체 없음");
        List<LessonResponseDTO> lessonResponseDTOS = new ArrayList<>();
        for (Lesson lesson : lessonPage)
            lessonResponseDTOS.add(this.lessonResponseDTO(lesson));

        return new PageImpl<>(lessonResponseDTOS, pageable, lessonPage.getTotalElements());
    }

    @Transactional
    public LessonResponseDTO updateLesson(String username, Long profileId, Long id, Long centerId, String name, String content, LocalDateTime startDate, LocalDateTime endDate) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (cultureCenter == null) throw new DataNotFoundException("센터 객체 없음");
        Lesson lesson = lessonService.findById(id);
        if (lesson == null)
            throw new DataNotFoundException("레슨 객체 없음");
        if (!lesson.getProfile().equals(profile))
            throw new IllegalArgumentException("레슨 강사 아님");
//        Optional<MultiKey> _oldMulti = multiKeyService.get(ImageKey.LESSON.getKey(lesson.getId().toString()));
//        if (_oldMulti.isPresent()) {
//            for (String k : key) {
//                Optional<FileSystem> _fileSystem = fileSystemService.get(k);
//                _fileSystem.ifPresent(fileSystem -> {
//                    fileSystemService.delete(fileSystem);
//                    _oldMulti.get().getVs().remove(key);
//                    deleteFile(_fileSystem.get());
//                });
//            }
//            multiKeyService.delete(_oldMulti.get());
//        }


        Lesson newlesson = lessonService.update(lesson, name, content, startDate, endDate);
        Optional<MultiKey> _multiKey = multiKeyService.get(ImageKey.TEMP.getKey(user.getUsername() + "." + profile.getId()));
        if (_multiKey.isPresent())
            newlesson = this.updateLessonContent(newlesson, _multiKey.get());

        return this.lessonResponseDTO(newlesson);
    }

    @Transactional
    public void deleteLesson(String username, Long profileId, Long lessonId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Lesson lesson = lessonService.findById(lessonId);
        if (lesson == null)
            throw new DataNotFoundException("레슨 객체 없음");
        if (!lesson.getProfile().equals(profile))
            throw new IllegalArgumentException("레슨 강사 아님");
        this.deleteLessonList(lesson);

    }

    private void deleteLessonList(Lesson lesson) {
        List<LessonUser> lessonUserList = lessonUserService.findByLessonId(lesson.getId());
        if (lessonUserList != null)
            for (LessonUser lessonUser : lessonUserList) {
                lessonUserService.delete(lessonUser);
            }
        this.lessonService.delete(lesson);
    }

    @Transactional
    public Page<LessonResponseDTO> getLessonStaff(String username, Long profileId, Long centerId, int page) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        CultureCenter cultureCenter = cultureCenterService.findById(centerId);
        if (cultureCenter == null)
            throw new DataNotFoundException("센터 객체 없음");
        if (!cultureCenter.getApt().equals(user.getApt()) || user.getRole() == UserRole.USER)
            throw new IllegalArgumentException("권한 없음");
        Pageable pageable = PageRequest.of(page, 10);
        Page<Lesson> lessonPage = lessonService.findByProfileAndCenter(profile.getId(), cultureCenter.getId(), pageable);
        List<LessonResponseDTO> lessonResponseDTOList = new ArrayList<>();
        for (Lesson lesson : lessonPage) {
            lessonResponseDTOList.add(this.lessonResponseDTO(lesson));
        }
        return new PageImpl<>(lessonResponseDTOList, pageable, lessonPage.getTotalElements());
    }


    /**
     * LessonUser
     */

    @Transactional
    public LessonUserResponseDTO saveLessonUser(String username, Long profileId, Long lessonId, int type) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Lesson lesson = lessonService.findById(lessonId);
        if (lesson == null)
            throw new DataNotFoundException("레슨 객체 없음");
        if (!user.getApt().equals(lesson.getCultureCenter().getApt()))
            throw new IllegalArgumentException("같은 아파트 아님");
        LessonUser lessonUser = lessonUserService.findByLessonAndProfile(lesson.getId(), profile.getId());
        if (lessonUser == null)
            lessonUser = lessonUserService.save(lesson, profile, type);
        return this.lessonUserResponseDTO(lessonUser);
    }

    private LessonUserResponseDTO lessonUserResponseDTO(LessonUser lessonUser) {
        return LessonUserResponseDTO.builder() //
                .id(lessonUser.getId()) //
                .lessonResponseDTO(this.lessonResponseDTO(lessonUser.getLesson())) //
                .profileResponseDTO(this.profileResponseDTO(lessonUser.getProfile()))//
                .type(lessonUser.getLessonStatus().toString()) //
                .build();
    }

    @Transactional
    public LessonUserResponseDTO getLessonUser(String username, Long profileId, Long lessonUserId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        LessonUser lessonUser = lessonUserService.findById(lessonUserId);
        if (lessonUser == null)
            throw new DataNotFoundException("레슨신청 객체 없음");
        if (!lessonUser.getProfile().equals(profile) && user.getRole() == UserRole.USER && !user.getApt().equals(lessonUser.getLesson().getCultureCenter().getApt()))
            throw new IllegalArgumentException("권한이 없음");
        return lessonUserResponseDTO(lessonUser);
    }

    @Transactional
    public List<LessonUserResponseDTO> getLessonUserMyList(String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        List<LessonUser> lessonUserList = lessonUserService.getMyList(profile);
        List<LessonUserResponseDTO> userResponseDTOS = new ArrayList<>();
        for (LessonUser lessonUser : lessonUserList) {
            userResponseDTOS.add(lessonUserResponseDTO(lessonUser));
        }
        return userResponseDTOS;
    }

    @Transactional
    public List<LessonUserResponseDTO> getLessonUserStaffList(String username, Long profileId, int type, Long lessonId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        Lesson lesson = lessonService.findById(lessonId);
        if (lesson == null)
            throw new DataNotFoundException("레슨 객체 없음");
        if (!lesson.getProfile().equals(profile))
            throw new IllegalArgumentException("레슨 강사 아님");
        List<LessonUser> lessonUserList = lessonUserService.getStaffList(lesson, type);
        List<LessonUserResponseDTO> userResponseDTOS = new ArrayList<>();
        for (LessonUser lessonUser : lessonUserList) {
            userResponseDTOS.add(this.lessonUserResponseDTO(lessonUser));
        }
        return userResponseDTOS;
    }

    @Transactional
    public LessonUserResponseDTO updateLessonUser(String username, Long profileId, Long id, int type) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        LessonUser lessonUser = lessonUserService.findById(id);
        if (lessonUser == null)
            throw new DataNotFoundException("레슨신청 객체 없음");
        if (!lessonUser.getProfile().equals(profile) && !lessonUser.getLesson().getProfile().equals(profile))
            throw new IllegalArgumentException("권한이 없음");
        return lessonUserResponseDTO(lessonUserService.update(lessonUser, type));
    }

    @Transactional
    public void deleteLessonUser(String username, Long profileId, Long lessonUserId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        LessonUser lessonUser = lessonUserService.findById(lessonUserId);
        if (lessonUser == null) throw new DataNotFoundException("레슨신청 객체 없음");
        if (!(lessonUser.getProfile().equals(profile) || lessonUser.getLesson().getProfile().equals(profile)) && user.getRole() == UserRole.USER)
            throw new IllegalArgumentException("권한 없음");
        lessonUserService.delete(lessonUser);
    }


    /**
     * Chat
     */

    @Transactional
    public ChatRoomResponseDTO saveChatRoom(String username, List<Long> targetProfileList, Long profileId, String title) {
        SiteUser sendUser = userService.get(username);
        Profile sendProfile = profileService.findById(profileId);
        this.userCheck(sendUser, sendProfile);
        ChatRoom chatRoom = chatRoomService.save(title);
        chatRoomUserService.save(sendProfile, chatRoom);
        for (Long id : targetProfileList) {
            if (!sendProfile.getId().equals(id)) {
                Profile targetProfile = profileService.findById(id);
                if (targetProfile == null)
                    throw new DataNotFoundException("상대 프로필 객체 없음");
                if (targetProfile.getUser().getRole() == UserRole.ADMIN)
                    throw new IllegalArgumentException("어드민한테 메세지를 보낼 수 없음");
                if (!sendProfile.getUser().getApt().equals(targetProfile.getUser().getApt()))
                    throw new IllegalArgumentException("같은 아파트 주민 아님");
                ChatRoomUser chatRoomUser = chatRoomUserService.findByProfile(chatRoom, targetProfile);
                if (chatRoomUser == null)
                    chatRoomUserService.save(targetProfile, chatRoom);
            }
        }

        return this.chatRoomResponseDTO(chatRoom, sendProfile);
    }

    private ChatRoomResponseDTO chatRoomResponseDTO(ChatRoom chatRoom, Profile profile) {
        List<ChatRoomUserResponseDTO> chatRoomUserResponseDTOS = new ArrayList<>();
        List<ChatRoomUser> chatRoomUsers = chatRoomUserService.findByChatRoomList(chatRoom);
        for (ChatRoomUser chatRoomUser : chatRoomUsers) {
            if (!profile.getUser().getApt().equals(chatRoomUser.getProfile().getUser().getApt()))
                throw new IllegalArgumentException("같은 아파트 주민 아님");
            chatRoomUserResponseDTOS.add(this.chatRoomUserResponseDTO(chatRoomUser));
        }
        List<ChatMessage> chatMessageList = chatMessageService.findByChatRoomList(chatRoom);
        List<ChatMessageResponseDTO> chatMessageResponseDTOS = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessageList) {
            chatMessageResponseDTOS.add(this.chatMessageResponseDTO(chatMessage));
        }

        return ChatRoomResponseDTO.builder().chatRoomId(chatRoom.getId()).chatMessageResponseDTOS(chatMessageResponseDTOS).title(chatRoom.getTitle()).chatRoomUserResponseDTOS(chatRoomUserResponseDTOS).createDate(this.dateTimeTransfer(chatRoom.getCreateDate())).build();
    }

    private ChatMessageResponseDTO chatMessageResponseDTO(ChatMessage chatMessage) {
        return ChatMessageResponseDTO.builder().id(chatMessage.getId()).profileResponseDTO(this.profileResponseDTO(chatMessage.getProfile())).createDate(this.dateTimeTransfer(chatMessage.getCreateDate())).build();
    }

    private ChatRoomUserResponseDTO chatRoomUserResponseDTO(ChatRoomUser chatRoomUser) {
        return ChatRoomUserResponseDTO.builder().id(chatRoomUser.getId()).profileName(chatRoomUser.getProfile().getName()).build();
    }

    @Transactional
    public ChatRoomResponseDTO ChatRoomDetail(String username, Long profileId, Long chatRoomId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        ChatRoom chatRoom = chatRoomService.findById(chatRoomId);
        if (chatRoom == null)
            throw new DataNotFoundException("채팅방 객체 없음");

        return this.chatRoomResponseDTO(chatRoom, profile);
    }

    @Transactional
    public List<ChatRoomResponseDTO> ChatRoomMyList(String username, Long profileId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        List<ChatRoomUser> chatRoomUserList = chatRoomUserService.getList(profile);
        List<ChatRoomResponseDTO> chatRoomResponseDTOS = new ArrayList<>();
        for (ChatRoomUser chatRoomUser : chatRoomUserList) {
            chatRoomResponseDTOS.add(this.chatRoomResponseDTO(chatRoomUser.getChatRoom(), profile));
        }
        return chatRoomResponseDTOS;
    }

    @Transactional
    public ChatRoomResponseDTO ChatRoomUpdate(String username, Long profileId, Long id, String title) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        ChatRoom targetChatRoom = chatRoomService.findById(id);
        if (targetChatRoom == null)
            throw new DataNotFoundException("채팅방 객체 없음");
        ChatRoom chatRoom = chatRoomService.chatRoomUpdate(targetChatRoom, title);

        return this.chatRoomResponseDTO(chatRoom, profile);
    }

    @Transactional
    public ChatRoomResponseDTO ChatRoomUserUpdate(String username, Long profileId, Long id, List<Long> targetProfileList) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        ChatRoom chatRoom = chatRoomService.findById(id);
        if (chatRoom == null)
            throw new DataNotFoundException("채팅방 객체 없음");
        ChatRoomUser chatRoomUser = chatRoomUserService.findByProfile(chatRoom, profile);
        if (chatRoomUser == null)
            throw new DataNotFoundException("채팅방에 있는 유저가 아님");
        for (Long targetProfileId : targetProfileList) {
            if (!profile.getId().equals(targetProfileId)) {
                Profile targetProfile = profileService.findById(targetProfileId);
                if (targetProfile == null)
                    throw new DataNotFoundException("상대 프로필 객체 없음");
                if (targetProfile.getUser().getRole() == UserRole.ADMIN)
                    throw new IllegalArgumentException("어드민한테 메세지를 보낼 수 없음");
                if (!profile.getUser().getApt().equals(targetProfile.getUser().getApt()))
                    throw new IllegalArgumentException("같은 아파트 주민 아님");
                ChatRoomUser targetChatRoomUser = chatRoomUserService.findByProfile(chatRoom, targetProfile);
                if (targetChatRoomUser == null)
                    chatRoomUserService.save(targetProfile, chatRoom);
            }
        }
        return this.chatRoomResponseDTO(chatRoom, profile);
    }

    @Transactional
    public void ChatRoomUserOut(String username, Long profileId, Long chatRoomId) {
        SiteUser user = userService.get(username);
        Profile profile = profileService.findById(profileId);
        this.userCheck(user, profile);
        ChatRoom chatRoom = chatRoomService.findById(chatRoomId);
        if (chatRoom == null)
            throw new DataNotFoundException("채팅방 객체 없음");
        ChatRoomUser chatRoomUser = chatRoomUserService.findByProfile(chatRoom, profile);
        if (chatRoomUser == null)
            throw new DataNotFoundException("채팅방에 있는 유저가 아님");
        chatRoomUserService.delete(chatRoomUser);
        List<ChatMessage> chatMessageList = chatMessageService.findByChatRoomList(chatRoom);
        for (ChatMessage chatMessage : chatMessageList) {
            chatMessageService.delete(chatMessage);
        }
        List<ChatRoomUser> chatRoomUserList = chatRoomUserService.findByChatRoomList(chatRoom);
        if (chatRoomUserList.isEmpty())
            chatRoomService.delete(chatRoom);

    }

    /**
     * Propose
     */

    @Transactional
    public ProposeResponseDTO savePropose(String title, String email, String roadAddress, String aptName, Integer max, Integer min, String password, Integer h, Integer w) {
        Propose propose = this.proposeService.save(title, email, roadAddress, aptName, max, min, password, h, w);
        return this.proposeResponseDTO(propose);
    }

    @Transactional
    public Page<ProposeResponseDTO> getProposePage(int page, int status) {
        Pageable pageable = PageRequest.of(page, 10);
        Page<Propose> proposePage = this.proposeService.getList(pageable, status);
        if (proposePage == null)
            throw new DataNotFoundException("신청 페이지 객체 없음");
        List<ProposeResponseDTO> proposeResponseDTOS = new ArrayList<>();
        for (Propose propose : proposePage)
            proposeResponseDTOS.add(this.proposeResponseDTO(propose));

        return new PageImpl<>(proposeResponseDTOS, pageable, proposePage.getTotalElements());
    }

    @Transactional
    public ProposeResponseDTO getPropose(String username, Long proposeId, String password) {
        SiteUser user = null;
        if (username != null) {
            user = this.userService.get(username);
        }
        Propose propose = this.proposeService.get(proposeId);
        if (user != null && user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("NOT AUTH");
        } else if (user == null && !this.proposeService.isMatchPropose(password, propose.getPassword())) {
            throw new IllegalArgumentException("NOT AUTH");
        }
        return this.proposeResponseDTO(propose);
    }


    @Transactional
    public ProposeResponseDTO updatePropose(String username, ProposeRequestDTO proposeRequestDTO) {
        SiteUser user = null;
        if (username != null) {
            user = this.userService.get(username);
        }
        Propose _propose = this.proposeService.get(proposeRequestDTO.getId());
        if (user != null && user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("NOT AUTH");
        } else if (user == null && !this.proposeService.isMatchPropose(proposeRequestDTO.getPassword(), _propose.getPassword())) {
            throw new IllegalArgumentException("NOT AUTH");
        }
        Propose propose = this.proposeService.update(_propose, proposeRequestDTO);

        return this.proposeResponseDTO(propose);
    }

    @Transactional
    public void deletePropose(String username, Long id, String password) {
        SiteUser user = null;
        if (username != null) {
            user = this.userService.get(username);
        }
        Propose propose = this.proposeService.get(id);
        if (user != null && user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("NOT AUTH");
        } else if (user == null && !this.proposeService.isMatchPropose(password, propose.getPassword())) {
            throw new IllegalArgumentException("NOT AUTH");
        }
        this.proposeService.delete(propose);
    }

    private ProposeResponseDTO proposeResponseDTO(Propose propose) {
        return ProposeResponseDTO.builder()
                .proposeStatus(propose.getProposeStatus().getStatus())
                .w(propose.getW())
                .h(propose.getH())
                .aptName(propose.getAptName())
                .min(propose.getMin())
                .max(propose.getMax())
                .createDate(this.dateTimeTransfer(propose.getCreateDate()))
                .modifyDate(this.dateTimeTransfer(propose.getModifyDate()))
                .email(propose.getEmail())
                .id(propose.getId())
                .roadAddress(propose.getRoadAddress())
                .title(propose.getTitle())
                .build();
    }

    @Transactional
    public void sendEmail (String username, EmailRequestDTO requestDTO) {
        SiteUser user = null;
        if (username != null) {
            user = this.userService.get(username);
        }
        if (user != null && user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("NOT AUTH");
        }
       this.emailService.mailSend(requestDTO);
    }

}
