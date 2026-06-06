package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.domain.Roles;
import com.yang.lblogserver.auth.domain.UserRoles;
import com.yang.lblogserver.auth.mapper.AuthorApplicationMapper;
import com.yang.lblogserver.auth.mapper.UserRolesMapper;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import com.yang.lblogserver.common.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorApplicationService {

    private final AuthorApplicationMapper applicationMapper;
    private final UsersMapper usersMapper;
    private final UserRolesMapper userRolesMapper;
    private final RoleService roleService;

    public AuthorApplicationService(AuthorApplicationMapper applicationMapper,
                                     UsersMapper usersMapper,
                                     UserRolesMapper userRolesMapper,
                                     RoleService roleService) {
        this.applicationMapper = applicationMapper;
        this.usersMapper = usersMapper;
        this.userRolesMapper = userRolesMapper;
        this.roleService = roleService;
    }

    /** 提交申请——每次创建新记录，保留历史 */
    public AuthorApplication submit(Long userId, String reason) {
        AuthorApplication latest = applicationMapper.selectByUserId(userId);
        if (latest != null && latest.getStatus() == 0) {
            throw new IllegalStateException("您已有待审核的申请，请耐心等待");
        }

        AuthorApplication app = new AuthorApplication();
        app.setUserId(userId);
        app.setReason(reason);
        app.setStatus(0);
        applicationMapper.insert(app);
        return app;
    }

    /** 查最新一条申请 */
    public AuthorApplication getByUserId(Long userId) {
        return applicationMapper.selectByUserId(userId);
    }

    /** 补充材料后重新提交——创建新记录 */
    public void resubmit(Long userId, String reason) {
        AuthorApplication latest = applicationMapper.selectByUserId(userId);
        if (latest == null) {
            throw new IllegalStateException("未找到申请记录");
        }
        if (latest.getStatus() != 2 && latest.getStatus() != 3) {
            throw new IllegalStateException("当前状态不允许重新提交");
        }

        AuthorApplication app = new AuthorApplication();
        app.setUserId(userId);
        app.setReason(reason);
        app.setStatus(0);
        applicationMapper.insert(app);
    }

    /** 管理端分页列表 */
    public PageResult<ApplicationVO> getApplicationList(int page, int pageSize,
                                                         Integer status, String keyword) {
        int offset = (page - 1) * pageSize;
        List<ApplicationVO> list = applicationMapper.selectApplicationList(status, keyword, offset, pageSize);
        int total = applicationMapper.countApplicationList(status, keyword);
        return PageResult.of(page, pageSize, total, list);
    }

    /** 审核 */
    @Transactional
    public void review(Long applicationId, Integer status, String feedback, Long reviewerId) {
        AuthorApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (app.getStatus() != 0) {
            throw new IllegalStateException("该申请已审核过");
        }

        // 拒绝(2)或需补充(3)时 feedback 必填
        if ((status == 2 || status == 3) && (feedback == null || feedback.isBlank())) {
            throw new IllegalArgumentException("拒绝或要求补充时必须填写反馈意见");
        }

        int rows = applicationMapper.updateReview(applicationId, status, feedback, reviewerId);
        if (rows == 0) {
            throw new IllegalStateException("该申请已被其他管理员审核");
        }

        // 通过时自动升级为作者
        if (status == 1) {
            usersMapper.updateRole(app.getUserId(), "author");

            // 同步 user_roles 表
            Roles authorRole = roleService.getByName("author");
            if (authorRole != null) {
                userRolesMapper.deleteByUserId(app.getUserId());
                UserRoles ur = new UserRoles();
                ur.setUserId(app.getUserId());
                ur.setRoleId(authorRole.getId());
                userRolesMapper.insertBatch(List.of(ur));
            }
        }
    }
}
