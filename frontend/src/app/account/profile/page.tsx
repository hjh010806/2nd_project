'use client'

import '@fortawesome/fontawesome-svg-core/styles.css'
import { getProfile, getProfileList, getUser, postProfile, saveImage, updateUser, updateUserPassword } from "@/app/API/UserAPI";
import DropDown, { Direcion } from "@/app/Global/DropDown";
import Modal from "@/app/Global/Modal";
import { redirect } from "next/navigation";
import { useEffect, useState } from "react";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faGear, faUserPlus, faBars, faEnvelope, faLock, faRightFromBracket } from "@fortawesome/free-solid-svg-icons";
import { Check, checkInput } from "@/app/Global/Method";
import { setFlagsFromString } from 'v8';

export default function Page() {
    const [user, setUser] = useState(null as any);
    const [profileList, setProfileList] = useState([] as any[]);
    const ACCESS_TOKEN = typeof window == 'undefined' ? null : localStorage.getItem('accessToken');
    const [isModalOpen, setISModalOpen] = useState(-1);
    const [url, setUrl] = useState('');
    const [name, setName] = useState('');
    const [openDropDown, setOpenDropDown] = useState(false);
    const [email, setEmail] = useState('');
    const [error, setError] = useState('');
    const [canShow, setCanShow] = useState(false);
    const [emailError, setEmailError] = useState('');
    const [oldPasswordError, setOldPasswordError] = useState('');
    const [newPassword1Error, setNewPassword1Error] = useState('');
    const [newPassword2Error, setNewPassword2Error] = useState('');
    const [emailConfirm, setEmailConfirm] = useState(false);
    const [passwordConfirm, setPasswordConfirm] = useState(false);
    const [profleConfirm, setProfleConfirm] = useState(false);
    const [profileId, setProfileId] = useState(null as any);
    const [profileName, setProfileName] = useState('');
    const [first, setFirst] = useState(true);

    useEffect(() => {
        if (ACCESS_TOKEN)
            getUser()
                .then(r => {
                    setUser(r);
                    setEmail(r.email);
                    getProfileList()
                        .then(r => setProfileList(r))
                        .catch(e => console.log(e));
                })
                .catch(e => console.log(e));
        else
            redirect('/account/login');
    }, [ACCESS_TOKEN]);
    
    function IsDisabledEamil() {
        return email == '' || email !== user?.email;
    }

    function EmailSubmit() {
        const updateEmail = (document.getElementById('email') as HTMLInputElement).value;

        // 이메일 형식 검사를 수행합니다.
        if (!Check('^[a-zA-Z0-9.+_-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$', updateEmail)) {
            setEmailError('이메일 형식이 맞지 않습니다.');
            setEmailConfirm(false);
            return;
        }

        updateUser({ email: email, password: "", newPassword1: "", newPassword2: "", name: "" })
            .then(r => {
                // 이메일 변경이 성공하면 사용자 정보를 업데이트합니다.
                setUser(r);
                setEmail(r.email);
                setEmailConfirm(false);
                openModal(-2);
            })
            .catch(error => {
                // 이메일 변경 중 오류가 발생하면 오류를 처리합니다.
                console.error('Error updating user:', error); // 오류 메시지 출력
                switch (error.response.data) {
                    case 'email':
                        setEmailError('이메일 중복');
                        setEmailConfirm(false);
                        break;
                    default:
                        console.log(error);
                        setEmailConfirm(false);
                }
            });
    }

    function openModal(type: number) {
        setISModalOpen(type);
    }

    function Change(file: any) {
        const formData = new FormData();
        formData.append('file', file);
        saveImage(formData)
            .then(r => setUrl(r?.url))
            .catch(e => console.log(e));
    }
    function ChangePassword() {
        // 비밀번호 입력 필드의 값을 가져옵니다.
        const old = (document.getElementById('old_password') as HTMLInputElement).value;
        const new1 = (document.getElementById('new_password1') as HTMLInputElement).value;
        const new2 = (document.getElementById('new_password2') as HTMLInputElement).value;

        // 새 비밀번호가 일치하는지 확인합니다.
        if (new1 !== new2) {
            setNewPassword2Error('변경할 비밀번호가 일치하지 않습니다.');
            setPasswordConfirm(false);
            return;
        }

        // 새 비밀번호가 올바른 형식인지 확인합니다.
        if (!Check('^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()-+={}~?:;`|/]).{6,24}$', new1)) {
            setNewPassword1Error('비밀번호는 최소 6자로 대문자, 소문자, 숫자, 특수문자가 한 개씩 들어가 있어야 합니다.');
            setPasswordConfirm(false);
            return;
        }
        if (!Check('^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()-+={}~?:;`|/]).{6,24}$', new2)) {
            setNewPassword2Error('변경할 비밀번호가 일치하지 않습니다.');
            setPasswordConfirm(false);
            return;
        }
        updateUserPassword({ name: user?.username, email: email, password: old, newPassword1: new1, newPassword2: new2 })
            .then(r => {
                // 비밀번호 변경이 성공하면 사용자 정보를 업데이트합니다.
                setUser(r);
                setPasswordConfirm(false);
                openModal(-2);
            })
            .catch(error => {
                // 비밀번호 변경 중 오류가 발생하면 오류를 처리합니다.
                console.log(error.response.data);
                switch (error.response.data) {
                    case "not match":
                        setNewPassword2Error('현재 비밀번호가 일치하지 않습니다.');
                        setPasswordConfirm(false);

                        break;
                    default:
                        console.log(error);
                        setPasswordConfirm(false);
                }
            });

    }


    function Select(id: number) {
        localStorage.setItem('PROFILE_ID', id.toString());
        getProfile()
            .then(() => {
                setProfleConfirm(false);
                console.log("profile selected!");
                // if (user.username === 'admin') {
                //     window.location.href = '/account/admin';
                // } else {
                window.location.href = '/';
                // }
            })
            .catch(e => console.log(e));
            setProfleConfirm(false);
    }


    function Regist() {
        if (profileList.length < 6) {
            postProfile({ name: name, url: url })
                .then(() => window.location.href = '/account/profile')
                .catch(e => console.log(e));
        } else {
            alert('프로필은 최대 6개까지 등록 가능합니다.');
            window.location.href = '/account/profile';
        }
    }

    function handleLogout(e: React.MouseEvent<HTMLButtonElement>) {
        e.preventDefault();
        localStorage.clear();
        window.location.reload();
    }

    const confirmEmail = () => {
        setEmailConfirm(true);
    };

    const confirmPassword = () => {
        setPasswordConfirm(true);
    };

    const passwordErrors = () => {
        if (oldPasswordError !== '')
            return oldPasswordError;
        if (newPassword1Error !== '' && oldPasswordError === '')
            return newPassword1Error;
        if (newPassword2Error !== '' && newPassword1Error === '' && oldPasswordError === '')
            return newPassword2Error;
    }

    function save (id: number, name: string) {
        setProfileId(id);
        setProfileName(name);
        setProfleConfirm(true);
    }

    return (
        <>
            <div className="bg-black flex flex-col items-center h-[953px] w-[1900px] relative" id="main">
                <div className="flex justify-end w-full mt-[15px] mr-[50px]">
                    <button id="profileSettings" className="btn btn-active btn-primary w-[180px] text-lg text-black" onClick={() => setOpenDropDown(!openDropDown)}>
                        <FontAwesomeIcon icon={faBars} />프로필 설정
                    </button>
                    <div>
                        <DropDown open={openDropDown} onClose={() => setOpenDropDown(false)} background="main" button="profileSettings" className="mt-[10px]" defaultDriection={Direcion.DOWN} height={200} width={180} x={-3} y={30}>
                            <button className="mt-0 btn btn-active btn-secondary text-lg text-black" onClick={() => openModal(1)}>
                                <FontAwesomeIcon icon={faGear} />계정 설정
                            </button>
                            <button className="mt-[5px] btn btn-active btn-secondary text-lg text-black" onClick={() => openModal(2)}>
                                <FontAwesomeIcon icon={faUserPlus} size="xs" />프로필 추가
                            </button>
                            <button onClick={user ? handleLogout : undefined} className="mt-[5px] btn btn-active btn-secondary text-lg text-black">
                                <FontAwesomeIcon icon={faRightFromBracket} className="mr-2" />
                                {user ? '로그아웃' : ''}
                            </button>
                        </DropDown>
                    </div>
                </div>
                <div className="h-1/5">
                    <div className="w-full flex flex-col items-center py-3">
                        <img src='/user.png' className='w-[64px] h-[64px] mb-2' alt="로고" />
                        <label className="font-bold text-2xl text-white">Honey Danji</label>
                    </div>
                </div>
                <div className="flex flex-wrap justify-center mx-auto mt-10 w-full">
                    {profileList?.map((profile, index) => (
                        <div key={index} className="text-center mx-auto my-3 w-1/3">
                            <div className="flex justify-center">
                                <button onClick={()=>save(profile.id, profile.name)}>
                                    <img src={profile?.url ? profile.url : '/user.png'} className="w-56 h-56 mb-2 mt-2 rounded-full" alt="프로필 이미지" />
                                    <span className='font-bold text-xl'>{profile?.name}</span>
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
            <Modal open={isModalOpen === 1} onClose={() => setISModalOpen(-1)} className='modal-box w-[700px] h-[800px] flex flex-col justify-center items-center' escClose={true} outlineClose={true} >
                <button className="btn btn-xl btn-circle text-xl text-black btn-ghost absolute right-2 top-2 hover:cursor-pointer" onClick={() => openModal(-2)}>✕</button>
                <div className="flex flex-col items-center gap-3">
                    <label className='text-xl text-black font-bold'><label className='text-xl text-secondary font-bold'>회원정보</label> 변경</label>
                    <label className='text-2xl font-bold text-secondary'><span className='text-black'>아이디 : </span>{user?.username}</label>
                    <label className='text-xs font-bold text-red-500'>{emailError}</label>
                    <FontAwesomeIcon icon={faEnvelope} className="text-gray-500 mr-2" />
                    <input id='email' type="text" className='input input-bordered input-lg text-black' defaultValue={email} minLength={3}
                        onChange={e => setEmail(e.target.value)}
                        onFocus={e => checkInput(e, '^[a-zA-Z0-9.+_-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$', () => setEmailError(''), () => setEmailError('이메일 형식이 맞지 않습니다.'))}
                        onKeyUp={e => checkInput(e, '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$', () => setEmailError(''), () => setEmailError('이메일 형식이 맞지 않습니다.'))} />
                    <button id='submit' className='w-[300px] btn btn-xl btn-accent mt-6 text-black' disabled={!IsDisabledEamil() || !!emailError} onClick={confirmEmail}>이메일 변경</button>
                    <p className='text-center w-[400px] mt-3 text-xs font-bold text-red-500'>{passwordErrors()}</p>
                    <FontAwesomeIcon icon={faLock} className="text-gray-500 mr-2" />

                    <input id="old_password" type={canShow ? 'text' : 'password'} className='w-[300px] mt-1 input input-bordered input-md text-black' placeholder='현재 비밀번호'
                        onKeyDown={e => { if (e.key == 'Enter') document.getElementById('new_password1')?.focus() }}
                        onFocus={e => { if ((e.target as HTMLInputElement).value == '') setOldPasswordError('현재 비밀번호를 입력해주세요.'); else setOldPasswordError('') }}
                        onKeyUp={e => { if ((e.target as HTMLInputElement).value == '') setOldPasswordError('현재 비밀번호를 입력해주세요.'); else setOldPasswordError('') }}
                        onChange={e => { if (first) setFirst(false); if ((e.target as HTMLInputElement).value == '') setOldPasswordError('현재 비밀번호를 입력해주세요.'); else setOldPasswordError('')}}
                    />

                    <input id="new_password1" type={canShow ? 'text' : 'password'} className='w-[300px] mt-1 input input-bordered input-md text-black' placeholder='변경 할 비밀번호'
                        onKeyDown={e => { if (e.key == 'Enter') document.getElementById('new_password2')?.focus() }}
                        onFocus={e => checkInput(e, '^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()-+={}~?:;`|/]).{6,24}$', () => setNewPassword1Error(''), () => setNewPassword1Error('비밀번호는 최소 6자로 대문자, 소문자, 숫자, 특수문자가 한개씩 들어가 있어야합니다.'))}
                        onKeyUp={e => checkInput(e, '^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()-+={}~?:;`|/]).{6,24}$', () => setNewPassword1Error(''), () => setNewPassword1Error('비밀번호는 최소 6자로 대문자, 소문자, 숫자, 특수문자가 한개씩 들어가 있어야합니다.'))}
                        onChange={e => { if (first) setFirst(false); checkInput(e, '^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[!@#$%^&*()-+={}~?:;`|/]).{6,24}$', () => setNewPassword1Error(''), () => setNewPassword1Error('비밀번호는 최소 6자로 대문자, 소문자, 숫자, 특수문자가 한개씩 들어가 있어야합니다.')) }} />

                    <input id="new_password2" type={canShow ? 'text' : 'password'} className='w-[300px] mt-1 input input-bordered input-md text-black' placeholder='비밀번호 확인'
                        onKeyDown={e => { if (e.key == 'Enter') document.getElementById('password_submit')?.click() }}
                        onFocus={e => {
                            if ((e.target as HTMLInputElement).value !== (document.getElementById('new_password1') as HTMLInputElement).value)
                                setNewPassword2Error('변경할 비밀번호가 일치하지 않습니다.'); else setNewPassword2Error('')
                        }}
                        onChange={e => {
                            if (first) setFirst(false);
                            if ((e.target as HTMLInputElement).value !== (document.getElementById('new_password1') as HTMLInputElement).value)
                                setNewPassword2Error('변경할 비밀번호가 일치하지 않습니다.'); else setNewPassword2Error('')
                        }} />
                    <div className="flex mt-2">
                        <label className='ml-1 text-sm text-black'>비밀번호 보이기</label>
                        <input className="ml-5" type='checkbox' onClick={() => setCanShow(!canShow)} />
                    </div>
                    <button className='w-[300px] btn btn-xl btn-accent mt-6 text-black' disabled={first || !!passwordErrors()} onClick={confirmPassword}>비밀번호 변경</button>
                </div>
            </Modal>
            <Modal open={isModalOpen === 2} onClose={() => setISModalOpen(-2)} className='modal-box w-[400px] h-[400px] flex flex-col justify-center items-center' escClose={true} outlineClose={true} >
                <button className="btn btn-xl btn-circle text-xl text-black btn-ghost absolute right-2 top-2 hover:cursor-pointer" onClick={() => openModal(-1)}> ✕ </button>
                <div className="relative w-[128px] h-auto flex justify-center items-center mb-10">
                    <div className="w-[128px] h-[128px] rounded-full opacity-30 absolute hover:bg-gray-500" onClick={() => document.getElementById('file')?.click()}></div>
                    <img src='/user.png' defaultValue={url} alt='main Image' className='w-[128px] h-[128px] rounded-full' />
                    <input id='file' hidden type='file' onChange={e => Change(e.target.files?.[0])} />
                </div>
                <div className="mt-0 flex flex-col items-center">
                    <label className='text-xs font-bold text-red-500 pb-5'>{error}</label>
                    <input id='name' type="text" defaultValue={name} onChange={e => setName(e.target.value)} className='input input-bordered input-lg text-black' placeholder="이름을 입력해주세요"
                        onFocus={e => checkInput(e, '^[가-힣]{1,6}$', () => setError(''), () => setError('프로필 이름은 6자 내외 한글만 가능합니다.'))}
                        onKeyUp={e => checkInput(e, '^[가-힣]{1,6}$', () => setError(''), () => setError('프로필 이름은 6자 내외 한글만 가능합니다.'))} />
                    <button className='btn btn-xl btn-accent mt-10 text-black' disabled={!!error} onClick={() => Regist()}>프로필 등록</button>
                </div>
            </Modal>
            {
                emailConfirm && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-gray-800 p-5 rounded shadow-lg">
                            <div className="text-lg font-semibold text-white">이메일 변경</div>
                            <p className="text-gray-400">이메일을 변경 하시겠습니까?</p>
                            <div className="mt-4 flex justify-end">
                                <button onClick={() => setEmailConfirm(false)} className="mr-2 p-2 bg-gray-600 rounded text-white hover:bg-gray-500">취소</button>
                                <button onClick={EmailSubmit} className="p-2 bg-yellow-600 rounded text-white hover:bg-yellow-500">변경</button>
                            </div>
                        </div>
                    </div>
                )
            }
            {
                passwordConfirm && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-gray-800 p-5 rounded shadow-lg">
                            <div className="text-lg font-semibold text-white">비밀번호 변경</div>
                            <p className="text-gray-400">비밀번호를 변경 하시겠습니까?</p>
                            <div className="mt-4 flex justify-end">
                                <button onClick={() => setPasswordConfirm(false)} className="mr-2 p-2 bg-gray-600 rounded text-white hover:bg-gray-500">취소</button>
                                <button onClick={ChangePassword} className="p-2 bg-yellow-600 rounded text-white hover:bg-yellow-500">변경</button>
                            </div>
                        </div>
                    </div>
                )
            }
            {
                profleConfirm && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-gray-800 p-5 rounded shadow-lg">
                            <div className="text-lg font-semibold text-secondary">{profileName}</div>
                            <p className="text-gray-400">해당 프로필로 로그인 하시겠습니까?</p>
                            <div className="mt-4 flex justify-end">
                                <button onClick={() => setProfleConfirm(false)} className="mr-2 p-2 bg-gray-600 rounded text-white hover:bg-gray-500">취소</button>
                                <button onClick={() => Select(profileId)} className="p-2 bg-yellow-600 rounded text-white hover:bg-yellow-500">로그인</button>
                            </div>
                        </div>
                    </div>
                )
            }

        </>
    );
}
