package com.yang.lblogserver.journal.controller;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.journal.service.JournalService;
import com.yang.lblogserver.journal.vo.CalendarDayVO;
import com.yang.lblogserver.journal.vo.CreateJournalRequest;
import com.yang.lblogserver.journal.vo.JournalVO;
import com.yang.lblogserver.journal.vo.UpdateJournalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Tag(name = "日记本", description = "个人日记管理")
@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户未认证");
        }
        return user.getUserId();
    }

    @GetMapping("/journals/calendar")
    @Operation(summary = "获取日历视图数据")
    public ApiResponse<List<CalendarDayVO>> calendar(@RequestParam int year, @RequestParam int month) {
        return ApiResponse.success(journalService.getCalendar(getCurrentUserId(), year, month));
    }

    @GetMapping("/journals")
    @Operation(summary = "获取时间线列表")
    public ApiResponse<List<JournalVO>> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.success(journalService.listJournals(getCurrentUserId(), page, pageSize));
    }

    @GetMapping("/journals/by-date")
    @Operation(summary = "查询某天的日记")
    public ApiResponse<JournalVO> getByDate(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        JournalVO vo = journalService.getByDate(getCurrentUserId(), date);
        return vo != null ? ApiResponse.success(vo) : ApiResponse.success(null);
    }

    @PostMapping("/journals")
    @Operation(summary = "新建日记（同一天已存在则更新）")
    public ApiResponse<JournalVO> create(@Valid @RequestBody CreateJournalRequest req) {
        return ApiResponse.success(journalService.create(getCurrentUserId(), req));
    }

    @PutMapping("/journals/{id}")
    @Operation(summary = "更新日记")
    public ApiResponse<JournalVO> update(@PathVariable Long id, @Valid @RequestBody UpdateJournalRequest req) {
        return ApiResponse.success(journalService.update(getCurrentUserId(), id, req));
    }

    @DeleteMapping("/journals/{id}")
    @Operation(summary = "删除日记（软删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        journalService.delete(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }
}
