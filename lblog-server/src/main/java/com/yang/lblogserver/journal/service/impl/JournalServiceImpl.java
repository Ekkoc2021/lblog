package com.yang.lblogserver.journal.service.impl;

import com.yang.lblogserver.journal.domain.Journal;
import com.yang.lblogserver.journal.mapper.JournalMapper;
import com.yang.lblogserver.journal.service.JournalService;
import com.yang.lblogserver.journal.vo.CalendarDayVO;
import com.yang.lblogserver.journal.vo.CreateJournalRequest;
import com.yang.lblogserver.journal.vo.JournalVO;
import com.yang.lblogserver.journal.vo.UpdateJournalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JournalServiceImpl implements JournalService {

    private final JournalMapper journalMapper;

    public JournalServiceImpl(JournalMapper journalMapper) {
        this.journalMapper = journalMapper;
    }

    @Override
    public List<CalendarDayVO> getCalendar(Long userId, int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date startDate = cal.getTime();
        cal.add(Calendar.MONTH, 1);
        Date endDate = cal.getTime();
        return journalMapper.selectByMonth(userId, startDate, endDate).stream()
                .map(j -> new CalendarDayVO(j.getJournalDate(), j.getMoodEmoji()))
                .collect(Collectors.toList());
    }

    @Override
    public List<JournalVO> listJournals(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return journalMapper.selectByUserId(userId, offset, pageSize).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public JournalVO getByDate(Long userId, Date date) {
        Journal j = journalMapper.selectByDate(userId, date);
        return j != null ? toVO(j) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JournalVO create(Long userId, CreateJournalRequest req) {
        Journal j = new Journal();
        j.setUserId(userId);
        j.setTitle(req.getTitle() != null ? req.getTitle() : "");
        j.setContent(req.getContent() != null ? req.getContent() : "");
        j.setMood(req.getMood() != null ? req.getMood() : "");
        j.setMoodEmoji(req.getMoodEmoji() != null ? req.getMoodEmoji() : "");
        j.setWeather(req.getWeather() != null ? req.getWeather() : "");
        j.setJournalDate(req.getJournalDate());
        journalMapper.upsert(j);
        // re-fetch for DB-generated fields (createdAt, updatedAt, id if insert)
        j = journalMapper.selectById(j.getId(), userId);
        return toVO(j);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JournalVO update(Long userId, Long id, UpdateJournalRequest req) {
        Journal j = journalMapper.selectById(id, userId);
        if (j == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        }
        if (req.getTitle() == null && req.getContent() == null
                && req.getMood() == null && req.getMoodEmoji() == null
                && req.getWeather() == null) {
            return toVO(j);
        }
        if (req.getTitle() != null) j.setTitle(req.getTitle());
        if (req.getContent() != null) j.setContent(req.getContent());
        if (req.getMood() != null) j.setMood(req.getMood());
        if (req.getMoodEmoji() != null) j.setMoodEmoji(req.getMoodEmoji());
        if (req.getWeather() != null) j.setWeather(req.getWeather());
        int rows = journalMapper.update(j, userId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在或已被删除");
        }
        j = journalMapper.selectById(id, userId);
        return toVO(j);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        int rows = journalMapper.softDelete(id, userId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        }
    }

    private JournalVO toVO(Journal j) {
        JournalVO vo = new JournalVO();
        vo.setId(j.getId());
        vo.setTitle(j.getTitle());
        vo.setContent(j.getContent());
        vo.setMood(j.getMood());
        vo.setMoodEmoji(j.getMoodEmoji());
        vo.setWeather(j.getWeather());
        vo.setJournalDate(j.getJournalDate());
        vo.setCreatedAt(j.getCreatedAt());
        vo.setUpdatedAt(j.getUpdatedAt());
        return vo;
    }
}
