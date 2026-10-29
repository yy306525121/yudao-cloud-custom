package cn.iocoder.yudao.module.system.service.social;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.socail.vo.user.NgiamBindRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.social.*;
import cn.iocoder.yudao.module.system.dal.mysql.social.*;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum.NGIAM_CAS;

@Service
public class NgiamBindingService {
    @Resource private SocialUserMapper socialUserMapper;
    @Resource private SocialUserBindMapper bindMapper;
    @Resource private AdminUserService userService;
    @Resource private NgiamCasService casService;
    @Resource private RedissonClient redissonClient;
    @Resource private TransactionTemplate transactionTemplate;

    public NgiamBindRespVO get(Long userId) {
        requireUser(userId);
        SocialUserBindDO bind = binding(userId);
        SocialUserDO social = bind == null ? null : socialUserMapper.selectById(bind.getSocialUserId());
        return new NgiamBindRespVO(userId, social == null ? null : social.getOpenid());
    }

    public void bind(Long userId, String username) {
        // A tenant-scoped distributed lock covers both identity and user conflicts, including absent rows.
        // The transaction commits BEFORE unlock; all IAM writes use this service.
        mutate(() -> {
            requireUser(userId);
            SocialUserDO social = casService.findExactUser(username);
            SocialUserBindDO current = binding(userId);
            if (current != null) {
                if (social != null && Objects.equals(current.getSocialUserId(), social.getId())) {
                    return;
                }
                throw exception(NGIAM_BIND_CONFLICT);
            }
            if (social != null && bindMapper.selectByUserTypeAndSocialUserId(
                    UserTypeEnum.ADMIN.getValue(), social.getId()) != null) {
                throw exception(NGIAM_BIND_CONFLICT);
            }
            if (social == null) {
                social = SocialUserDO.builder().type(NGIAM_CAS.getType()).openid(username).nickname(username)
                        .code("").state("").token("").rawTokenInfo("{}").rawUserInfo("{}").build();
                socialUserMapper.insert(social);
            }
            bindMapper.insert(SocialUserBindDO.builder().socialType(NGIAM_CAS.getType())
                    .socialUserId(social.getId()).userId(userId).userType(UserTypeEnum.ADMIN.getValue()).build());
        });
    }

    public void unbind(Long userId) {
        mutate(() -> {
            requireUser(userId);
            bindMapper.deleteByUserTypeAndUserIdAndSocialType(UserTypeEnum.ADMIN.getValue(), userId, NGIAM_CAS.getType());
        });
    }

    private SocialUserBindDO binding(Long userId) {
        return bindMapper.selectByUserIdAndUserTypeAndSocialType(userId, UserTypeEnum.ADMIN.getValue(), NGIAM_CAS.getType());
    }

    private void requireUser(Long userId) {
        if (userService.getUser(userId) == null) {
            throw exception(USER_NOT_EXISTS);
        }
    }

    private void mutate(Runnable action) {
        RLock lock = redissonClient.getLock("system:ngiam:binding:" + TenantContextHolder.getRequiredTenantId());
        lock.lock(); // Redisson watchdog renews while the transaction is running.
        try {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } finally {
            lock.unlock();
        }
    }
}
