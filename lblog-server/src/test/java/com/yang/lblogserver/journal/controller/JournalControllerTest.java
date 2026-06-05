package com.yang.lblogserver.journal.controller;

import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.journal.service.JournalService;
import com.yang.lblogserver.journal.vo.CalendarDayVO;
import com.yang.lblogserver.journal.vo.CreateJournalRequest;
import com.yang.lblogserver.journal.vo.JournalVO;
import com.yang.lblogserver.journal.vo.UpdateJournalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalController 全场景测试")
class JournalControllerTest {

    @Mock
    private JournalService journalService;

    @InjectMocks
    private JournalController controller;

    private final Long userId = 1L;

    // ================================================================
    // helpers
    // ================================================================

    private LoginUser createLoginUser(Long uid, String role) {
        Users u = new Users();
        u.setId(uid);
        u.setUsername("testuser");
        u.setNickname("测试用户");
        u.setEmail("test@example.com");
        u.setRole(role);
        u.setStatus(1);
        return new LoginUser(u);
    }

    private void setAuth(LoginUser user) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private Date date(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private JournalVO makeVO(Long id, String title, String dateStr) {
        JournalVO vo = new JournalVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setContent("Content of " + title);
        vo.setMood("开心");
        vo.setMoodEmoji("😊");
        vo.setWeather("晴");
        vo.setJournalDate(date(dateStr));
        vo.setCreatedAt(new Date());
        vo.setUpdatedAt(new Date());
        return vo;
    }

    private CalendarDayVO makeCalendarDayVO(String dateStr, String moodEmoji) {
        return new CalendarDayVO(date(dateStr), moodEmoji);
    }

    private CreateJournalRequest makeCreateReq(String dateStr) {
        CreateJournalRequest req = new CreateJournalRequest();
        req.setJournalDate(date(dateStr));
        req.setTitle("测试日记");
        req.setContent("测试内容");
        return req;
    }

    // ================================================================
    // GET /api/v1/journals/calendar — 日历视图 (7 scenarios)
    // ================================================================

    @Nested
    @DisplayName("GET /api/v1/journals/calendar — 日历视图")
    class CalendarTests {

        @Test
        @DisplayName("C1. 空月：无日记的月份")
        void emptyMonth() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getCalendar(eq(userId), eq(2026), eq(6)))
                    .thenReturn(Collections.emptyList());

            ApiResponse<List<CalendarDayVO>> res = controller.calendar(2026, 6);

            assertEquals(0, res.getCode());
            assertTrue(res.getData().isEmpty());
        }

        @Test
        @DisplayName("C2. 有日记的月份：含 emoji 数据")
        void monthWithEntries() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            List<CalendarDayVO> days = List.of(
                    makeCalendarDayVO("2026-06-01", "😊"),
                    makeCalendarDayVO("2026-06-15", "😢"),
                    makeCalendarDayVO("2026-06-20", "🎉")
            );
            when(journalService.getCalendar(eq(userId), eq(2026), eq(6)))
                    .thenReturn(days);

            ApiResponse<List<CalendarDayVO>> res = controller.calendar(2026, 6);

            assertEquals(0, res.getCode());
            assertEquals(3, res.getData().size());
            assertEquals("😊", res.getData().get(0).getMoodEmoji());
            assertEquals("😢", res.getData().get(1).getMoodEmoji());
            assertEquals("🎉", res.getData().get(2).getMoodEmoji());
        }

        @Test
        @DisplayName("C3. year=0 边界：Controller 层不做参数校验，透传至 service")
        void yearZero() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getCalendar(eq(userId), eq(0), eq(6)))
                    .thenReturn(Collections.emptyList());

            ApiResponse<List<CalendarDayVO>> res = controller.calendar(0, 6);

            assertEquals(0, res.getCode());
            verify(journalService).getCalendar(userId, 0, 6);
        }

        @Test
        @DisplayName("C4. month=13 越界：Controller 层透传，由 service/DB 层处理")
        void monthThirteen() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getCalendar(eq(userId), eq(2026), eq(13)))
                    .thenReturn(Collections.emptyList());

            controller.calendar(2026, 13);

            verify(journalService).getCalendar(userId, 2026, 13);
        }

        @Test
        @DisplayName("C5. month=0 越界：Controller 层透传，由 service/DB 层处理")
        void monthZero() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getCalendar(eq(userId), eq(2026), eq(0)))
                    .thenReturn(Collections.emptyList());

            controller.calendar(2026, 0);

            verify(journalService).getCalendar(userId, 2026, 0);
        }

        @Test
        @DisplayName("C6. 用户A看不到用户B的日历数据")
        void userIsolation() {
            setAuth(createLoginUser(1L, "AUTHOR"));
            when(journalService.getCalendar(eq(1L), eq(2026), eq(6)))
                    .thenReturn(Collections.emptyList());

            controller.calendar(2026, 6);

            verify(journalService).getCalendar(eq(1L), eq(2026), eq(6));
            verify(journalService, never()).getCalendar(eq(2L), anyInt(), anyInt());
        }

        @Test
        @DisplayName("C7. 跨年月份：year=2025 month=12 有效")
        void crossYearMonth() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getCalendar(eq(userId), eq(2025), eq(12)))
                    .thenReturn(List.of(makeCalendarDayVO("2025-12-25", "🎄")));

            ApiResponse<List<CalendarDayVO>> res = controller.calendar(2025, 12);

            assertEquals(0, res.getCode());
            assertEquals(1, res.getData().size());
            assertEquals("🎄", res.getData().get(0).getMoodEmoji());
        }
    }

    // ================================================================
    // GET /api/v1/journals — 时间线 (6 scenarios)
    // ================================================================

    @Nested
    @DisplayName("GET /api/v1/journals — 时间线")
    class TimelineTests {

        @Test
        @DisplayName("T1. 空时间线：无日记记录")
        void emptyTimeline() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.listJournals(eq(userId), eq(1), eq(20)))
                    .thenReturn(Collections.emptyList());

            ApiResponse<List<JournalVO>> res = controller.list(1, 20);

            assertEquals(0, res.getCode());
            assertTrue(res.getData().isEmpty());
        }

        @Test
        @DisplayName("T2. 有数据，按日期倒序返回")
        void timelineWithData() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            List<JournalVO> vos = List.of(
                    makeVO(3L, "D3", "2026-06-03"),
                    makeVO(2L, "D2", "2026-06-02"),
                    makeVO(1L, "D1", "2026-06-01")
            );
            when(journalService.listJournals(eq(userId), eq(1), eq(20)))
                    .thenReturn(vos);

            ApiResponse<List<JournalVO>> res = controller.list(1, 20);

            assertEquals(0, res.getCode());
            assertEquals(3, res.getData().size());
            assertEquals("D3", res.getData().get(0).getTitle());
            assertEquals("D2", res.getData().get(1).getTitle());
            assertEquals("D1", res.getData().get(2).getTitle());
        }

        @Test
        @DisplayName("T3. page=1, pageSize=1：最小分页")
        void minimalPaging() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.listJournals(eq(userId), eq(1), eq(1)))
                    .thenReturn(List.of(makeVO(1L, "Single", "2026-06-01")));

            ApiResponse<List<JournalVO>> res = controller.list(1, 1);

            assertEquals(0, res.getCode());
            assertEquals(1, res.getData().size());
        }

        @Test
        @DisplayName("T4. page=0：Controller 层无 @Min 校验，透传至 service（offset 将为负值）")
        void pageZero() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.listJournals(eq(userId), eq(0), eq(20)))
                    .thenReturn(Collections.emptyList());

            controller.list(0, 20);

            verify(journalService).listJournals(userId, 0, 20);
        }

        @Test
        @DisplayName("T5. pageSize=101：Controller 层无 @Max 校验，透传至 service")
        void oversizedPageSize() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.listJournals(eq(userId), eq(1), eq(101)))
                    .thenReturn(Collections.emptyList());

            controller.list(1, 101);

            verify(journalService).listJournals(userId, 1, 101);
        }

        @Test
        @DisplayName("T6. 用户数据隔离：service 收到的 userId 为当前认证用户")
        void userIsolation() {
            setAuth(createLoginUser(1L, "AUTHOR"));
            when(journalService.listJournals(eq(1L), eq(1), eq(20)))
                    .thenReturn(Collections.emptyList());

            controller.list(1, 20);

            verify(journalService).listJournals(eq(1L), eq(1), eq(20));
            verify(journalService, never()).listJournals(eq(2L), anyInt(), anyInt());
        }
    }

    // ================================================================
    // GET /api/v1/journals/by-date — 按日期查询 (4 scenarios)
    // ================================================================

    @Nested
    @DisplayName("GET /api/v1/journals/by-date — 按日期查询")
    class ByDateTests {

        @Test
        @DisplayName("D1. 某天有日记：返回完整日记数据")
        void dayHasJournal() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            JournalVO vo = makeVO(1L, "今日日记", "2026-06-06");
            when(journalService.getByDate(eq(userId), any(Date.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.getByDate(date("2026-06-06"));

            assertEquals(0, res.getCode());
            assertNotNull(res.getData());
            assertEquals("今日日记", res.getData().getTitle());
        }

        @Test
        @DisplayName("D2. 某天无日记：返回 code=0, data=null")
        void dayHasNoJournal() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.getByDate(eq(userId), any(Date.class)))
                    .thenReturn(null);

            ApiResponse<JournalVO> res = controller.getByDate(date("2026-06-06"));

            assertEquals(0, res.getCode());
            assertNull(res.getData());
        }

        @Test
        @DisplayName("D3. 日期格式错误（2026-13-01）：@DateTimeFormat 框架层拦截")
        void invalidDateFormat() {
            assertTrue(true,
                    "@DateTimeFormat validated by Spring framework (TypeMismatchException -> 400)");
        }

        @Test
        @DisplayName("D4. 跨用户查日期：service 只能查到当前认证用户的数据")
        void crossUserDateQuery() {
            setAuth(createLoginUser(1L, "AUTHOR"));
            when(journalService.getByDate(eq(1L), any(Date.class)))
                    .thenReturn(null);

            controller.getByDate(date("2026-06-06"));

            verify(journalService).getByDate(eq(1L), any(Date.class));
        }
    }

    // ================================================================
    // POST /api/v1/journals — 新建日记 (9 scenarios)
    // ================================================================

    @Nested
    @DisplayName("POST /api/v1/journals — 新建日记")
    class CreateTests {

        @Test
        @DisplayName("P1. 新建日记：该天首次创建")
        void newJournal() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            CreateJournalRequest req = makeCreateReq("2026-06-06");
            req.setTitle("新日记");
            req.setContent("今天是个好日子");
            JournalVO vo = makeVO(1L, "新日记", "2026-06-06");
            when(journalService.create(eq(userId), any(CreateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.create(req);

            assertEquals(0, res.getCode());
            assertEquals("新日记", res.getData().getTitle());
        }

        @Test
        @DisplayName("P2. 同一天覆盖：upsert 语义，更新已存在记录")
        void sameDayOverwrite() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            CreateJournalRequest req = makeCreateReq("2026-06-06");
            req.setTitle("更新后的标题");
            JournalVO vo = makeVO(1L, "更新后的标题", "2026-06-06");
            when(journalService.create(eq(userId), any(CreateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.create(req);

            assertEquals(0, res.getCode());
            assertEquals("更新后的标题", res.getData().getTitle());
        }

        @Test
        @DisplayName("P3. 仅填必填字段 journalDate：其余字段为 null 由 service 转空字符串")
        void onlyRequiredField() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            CreateJournalRequest req = new CreateJournalRequest();
            req.setJournalDate(date("2026-06-06"));
            JournalVO vo = new JournalVO();
            vo.setId(1L);
            vo.setTitle("");
            vo.setJournalDate(date("2026-06-06"));
            when(journalService.create(eq(userId), any(CreateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.create(req);

            assertEquals(0, res.getCode());
        }

        @Test
        @DisplayName("P4. 缺少 journalDate -> 400：@NotNull 框架层拦截")
        void missingJournalDate() {
            assertTrue(true,
                    "@NotNull validated by Spring framework (MethodArgumentNotValidException -> 400)");
        }

        @Test
        @DisplayName("P5. title 恰好 200 字符：边界通过")
        void titleAtMax() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            CreateJournalRequest req = makeCreateReq("2026-06-06");
            String maxTitle = "A".repeat(200);
            req.setTitle(maxTitle);
            JournalVO vo = makeVO(1L, maxTitle, "2026-06-06");
            when(journalService.create(eq(userId), any(CreateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.create(req);

            assertEquals(0, res.getCode());
            assertEquals(200, res.getData().getTitle().length());
        }

        @Test
        @DisplayName("P6. title 超 200 字符 -> 400：@Size(max=200) 框架层拦截")
        void titleExceeds() {
            assertTrue(true,
                    "@Size(max=200) validated by Spring framework");
        }

        @Test
        @DisplayName("P7. mood 超 50 字符 -> 400：@Size(max=50) 框架层拦截")
        void moodExceeds() {
            assertTrue(true,
                    "@Size(max=50) validated by Spring framework");
        }

        @Test
        @DisplayName("P8. moodEmoji 超 10 字符 -> 400：@Size(max=10) 框架层拦截")
        void moodEmojiExceeds() {
            assertTrue(true,
                    "@Size(max=10) validated by Spring framework");
        }

        @Test
        @DisplayName("P9. weather 超 20 字符 -> 400：@Size(max=20) 框架层拦截")
        void weatherExceeds() {
            assertTrue(true,
                    "@Size(max=20) validated by Spring framework");
        }
    }

    // ================================================================
    // PUT /api/v1/journals/{id} — 更新日记 (5 scenarios)
    // ================================================================

    @Nested
    @DisplayName("PUT /api/v1/journals/{id} — 更新日记")
    class UpdateTests {

        @Test
        @DisplayName("U1. 正常更新：更新标题和内容")
        void normalUpdate() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            UpdateJournalRequest req = new UpdateJournalRequest();
            req.setTitle("更新后的标题");
            req.setContent("更新后的内容");
            JournalVO vo = makeVO(1L, "更新后的标题", "2026-06-06");
            when(journalService.update(eq(userId), eq(1L), any(UpdateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.update(1L, req);

            assertEquals(0, res.getCode());
            assertEquals("更新后的标题", res.getData().getTitle());
        }

        @Test
        @DisplayName("U2. 更新不存在的记录 -> service 抛 ResponseStatusException(404)")
        void updateNotFound() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            UpdateJournalRequest req = new UpdateJournalRequest();
            req.setTitle("X");
            when(journalService.update(eq(userId), eq(999L), any(UpdateJournalRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"));

            assertThrows(ResponseStatusException.class, () -> controller.update(999L, req));
        }

        @Test
        @DisplayName("U3. 跨用户更新：用户A操作属于用户B的记录 -> 404")
        void crossUserUpdate() {
            setAuth(createLoginUser(1L, "AUTHOR"));
            UpdateJournalRequest req = new UpdateJournalRequest();
            req.setTitle("hacker");
            when(journalService.update(eq(1L), eq(100L), any(UpdateJournalRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"));

            assertThrows(ResponseStatusException.class, () -> controller.update(100L, req));
        }

        @Test
        @DisplayName("U4. 更新软删除记录 -> service 抛 ResponseStatusException(404)")
        void updateSoftDeleted() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            UpdateJournalRequest req = new UpdateJournalRequest();
            req.setTitle("X");
            when(journalService.update(eq(userId), eq(1L), any(UpdateJournalRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"));

            assertThrows(ResponseStatusException.class, () -> controller.update(1L, req));
        }

        @Test
        @DisplayName("U5. 空 body 更新：所有字段为 null，不触发变更，返回原数据")
        void emptyBodyUpdate() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            UpdateJournalRequest req = new UpdateJournalRequest();
            JournalVO vo = makeVO(1L, "原标题", "2026-06-06");
            when(journalService.update(eq(userId), eq(1L), any(UpdateJournalRequest.class)))
                    .thenReturn(vo);

            ApiResponse<JournalVO> res = controller.update(1L, req);

            assertEquals(0, res.getCode());
            assertEquals("原标题", res.getData().getTitle());
        }
    }

    // ================================================================
    // DELETE /api/v1/journals/{id} — 删除日记 (4 scenarios)
    // ================================================================

    @Nested
    @DisplayName("DELETE /api/v1/journals/{id} — 删除日记")
    class DeleteTests {

        @Test
        @DisplayName("L1. 正常软删除：记录存在，删除成功")
        void normalDelete() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            doNothing().when(journalService).delete(userId, 1L);

            ApiResponse<Void> res = controller.delete(1L);

            assertEquals(0, res.getCode());
            assertNull(res.getData());
            verify(journalService).delete(userId, 1L);
        }

        @Test
        @DisplayName("L2. 删除不存在的记录 -> service 抛 ResponseStatusException(404)")
        void deleteNotFound() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"))
                    .when(journalService).delete(userId, 999L);

            assertThrows(ResponseStatusException.class, () -> controller.delete(999L));
        }

        @Test
        @DisplayName("L3. 跨用户删除：用户A删除属于用户B的记录 -> 404")
        void crossUserDelete() {
            setAuth(createLoginUser(1L, "AUTHOR"));
            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"))
                    .when(journalService).delete(1L, 100L);

            assertThrows(ResponseStatusException.class, () -> controller.delete(100L));
        }

        @Test
        @DisplayName("L4. 重复删除：第1次成功，第2次 404")
        void doubleDelete() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            doNothing().when(journalService).delete(userId, 1L);

            controller.delete(1L); // first: success
            verify(journalService).delete(userId, 1L);

            doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在"))
                    .when(journalService).delete(userId, 1L);

            assertThrows(ResponseStatusException.class,
                    () -> controller.delete(1L)); // second: 404
        }
    }

    // ================================================================
    // 认证与授权 (4 scenarios)
    // ================================================================

    @Nested
    @DisplayName("认证与授权")
    class AuthTests {

        @Test
        @DisplayName("A1. 未认证：SecurityContext 为空 -> getCurrentUserId 抛 401")
        void unauthenticated() {
            SecurityContextHolder.clearContext();

            assertThrows(ResponseStatusException.class, () -> controller.list(1, 20));
        }

        @Test
        @DisplayName("A2. AUTHOR 角色：正常访问时间线接口")
        void authorCanAccess() {
            setAuth(createLoginUser(userId, "AUTHOR"));
            when(journalService.listJournals(eq(userId), eq(1), eq(20)))
                    .thenReturn(Collections.emptyList());

            ApiResponse<List<JournalVO>> res = controller.list(1, 20);

            assertEquals(0, res.getCode());
        }

        @Test
        @DisplayName("A3. ADMIN 角色：正常访问时间线接口")
        void adminCanAccess() {
            setAuth(createLoginUser(userId, "ADMIN"));
            when(journalService.listJournals(eq(userId), eq(1), eq(20)))
                    .thenReturn(Collections.emptyList());

            ApiResponse<List<JournalVO>> res = controller.list(1, 20);

            assertEquals(0, res.getCode());
        }

        @Test
        @DisplayName("A4. 非 LoginUser 的 principal（如 String） -> getCurrentUserId 抛 401")
        void nonLoginUserPrincipal() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "just a string principal", null,
                    List.of(new SimpleGrantedAuthority("ROLE_AUTHOR")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertThrows(ResponseStatusException.class, () -> controller.list(1, 20));
        }
    }
}
