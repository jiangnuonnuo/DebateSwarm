package com.dasi.domain.room.service.room.impl;

import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.user.model.vo.UserVO;
import com.dasi.domain.user.repository.IUserRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.room.impl
 * @Author: xerina
 * @CreateTime: 2026-03-25  18:00
 * @Description: 用户成员处理策略
 */
@Service("USER_MEMBER")
public class UserMemberService extends AbstractRoomMemberService {

    @Resource
    private IUserRepository userRepository;

    @Override
    public String queryMemberName(String memberId, String memberType) {
        // memberId 对于 USER 类型来说就是 username
        UserVO userVO = userRepository.queryUserByUserName(memberId);
        return userVO != null ? userVO.getUserName() : memberId;
    }

    @Override
    protected boolean doJoin(AiChatRoomMemberEntity memberEntity) {
        // 用户加入无额外加工逻辑
        return true;
    }
}
