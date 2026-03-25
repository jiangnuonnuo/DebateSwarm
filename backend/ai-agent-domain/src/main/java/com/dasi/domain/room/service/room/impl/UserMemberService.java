package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.room.service.room.IRoomMemberInsetService;
import com.dasi.domain.user.model.vo.UserVO;
import com.dasi.domain.user.repository.IUserRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  18:00
 * @Description: TODO
 */

@Service("USER_MEMBER")
public class UserMemberService implements IRoomMemberInsetService {

    @Resource
    private IUserRepository userRepository;

    @Override
    public String queryMemberName(String memberId, String memberType) {
        // memberId 对于 USER 类型来说就是 username
        UserVO userVO = userRepository.queryUserByUserName(memberId);
        return userVO != null ? userVO.getUserName() : memberId;
    }
}
