package com.yang.lblogserver.journal.service;

import com.yang.lblogserver.journal.vo.CalendarDayVO;
import com.yang.lblogserver.journal.vo.CreateJournalRequest;
import com.yang.lblogserver.journal.vo.JournalVO;
import com.yang.lblogserver.journal.vo.UpdateJournalRequest;
import java.util.Date;
import java.util.List;

public interface JournalService {
    List<CalendarDayVO> getCalendar(Long userId, int year, int month);
    List<JournalVO> listJournals(Long userId, int page, int pageSize);
    JournalVO getByDate(Long userId, Date date);
    JournalVO create(Long userId, CreateJournalRequest req);
    JournalVO update(Long userId, Long id, UpdateJournalRequest req);
    void delete(Long userId, Long id);
}
