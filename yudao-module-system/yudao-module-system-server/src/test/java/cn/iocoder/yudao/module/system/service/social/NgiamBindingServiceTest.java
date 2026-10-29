package cn.iocoder.yudao.module.system.service.social;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.social.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.social.*;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.*;
import org.redisson.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NgiamBindingServiceTest {
    private NgiamBindingService service;
    private SocialUserMapper users;
    private SocialUserBindMapper bindings;
    private NgiamCasService cas;
    private AdminUserService admins;
    private PlatformTransactionManager transactions;
    private TransactionStatus status;
    private RLock lock;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        service = new NgiamBindingService(); users = mock(SocialUserMapper.class);
        bindings = mock(SocialUserBindMapper.class); cas = mock(NgiamCasService.class);
        admins = mock(AdminUserService.class);
        transactions = mock(PlatformTransactionManager.class); status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(status);
        RedissonClient redisson = mock(RedissonClient.class); lock = mock(RLock.class);
        when(redisson.getLock("system:ngiam:binding:7")).thenReturn(lock);
        ReflectionTestUtils.setField(service, "socialUserMapper", users);
        ReflectionTestUtils.setField(service, "bindMapper", bindings);
        ReflectionTestUtils.setField(service, "casService", cas);
        ReflectionTestUtils.setField(service, "userService", admins);
        ReflectionTestUtils.setField(service, "redissonClient", redisson);
        ReflectionTestUtils.setField(service, "transactionTemplate", new TransactionTemplate(transactions));
        when(admins.getUser(9L)).thenReturn(new AdminUserDO());
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void prebindsIdentityBeforeFirstLoginAndCommitsBeforeUnlock() {
        doAnswer(inv -> { inv.getArgument(0, SocialUserDO.class).setId(8L); return 1; }).when(users).insert(any(SocialUserDO.class));
        service.bind(9L, "Alice");
        verify(users).insert(argThat((SocialUserDO user) -> "Alice".equals(user.getOpenid()) && "".equals(user.getCode())));
        verify(bindings).insert(argThat((SocialUserBindDO bind) -> bind.getUserId() == 9L && bind.getSocialUserId() == 8L));
        var order = inOrder(lock, transactions);
        order.verify(lock).lock(); order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(status); order.verify(lock).unlock();
    }

    @Test void rejectsIdentityBoundToAnotherUserAndRollsBack() {
        when(cas.findExactUser("Alice")).thenReturn(SocialUserDO.builder().id(8L).build());
        when(bindings.selectByUserTypeAndSocialUserId(2, 8L)).thenReturn(SocialUserBindDO.builder().userId(10L).build());
        assertServiceException(() -> service.bind(9L, "Alice"), NGIAM_BIND_CONFLICT);
        verify(transactions).rollback(status); verify(lock).unlock();
        verify(bindings, never()).insert(any(SocialUserBindDO.class));
    }

    @Test void rejectsReplacingExistingUserBinding() {
        when(bindings.selectByUserIdAndUserTypeAndSocialType(9L, 2, 35))
                .thenReturn(SocialUserBindDO.builder().socialUserId(10L).build());
        assertServiceException(() -> service.bind(9L, "Alice"), NGIAM_BIND_CONFLICT);
    }

    @Test void sameBindingIsIdempotent() {
        when(cas.findExactUser("Alice")).thenReturn(SocialUserDO.builder().id(8L).build());
        when(bindings.selectByUserIdAndUserTypeAndSocialType(9L, 2, 35))
                .thenReturn(SocialUserBindDO.builder().socialUserId(8L).build());
        service.bind(9L, "Alice");
        verify(bindings, never()).insert(any(SocialUserBindDO.class));
    }

    @Test void unbindsOnlyIamForRequestedUser() {
        service.unbind(9L);
        verify(bindings).deleteByUserTypeAndUserIdAndSocialType(2, 9L, 35);
        verify(transactions).commit(status);
    }

    @Test void missingLocalUserCannotBeBound() {
        assertServiceException(() -> service.bind(10L, "Alice"), USER_NOT_EXISTS);
        verifyNoInteractions(users, bindings);
    }
}
