package com.yang.lblogserver.journal.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "日历视图某天数据")
public class CalendarDayVO {
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date journalDate;
    private String moodEmoji;

    public CalendarDayVO() {}
    public CalendarDayVO(Date journalDate, String moodEmoji) {
        this.journalDate = journalDate;
        this.moodEmoji = moodEmoji;
    }

    public Date getJournalDate() { return journalDate; }
    public void setJournalDate(Date journalDate) { this.journalDate = journalDate; }
    public String getMoodEmoji() { return moodEmoji; }
    public void setMoodEmoji(String moodEmoji) { this.moodEmoji = moodEmoji; }
}
