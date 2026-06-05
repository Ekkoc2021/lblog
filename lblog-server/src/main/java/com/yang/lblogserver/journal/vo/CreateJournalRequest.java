package com.yang.lblogserver.journal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@Schema(description = "创建日记请求")
public class CreateJournalRequest {

    @Size(max = 200)
    @Schema(description = "标题")
    private String title;

    @Size(max = 20000)
    @Schema(description = "正文")
    private String content;

    @Size(max = 50)
    @Schema(description = "心情标签")
    private String mood;

    @Size(max = 10)
    @Schema(description = "心情emoji")
    private String moodEmoji;

    @Size(max = 20)
    @Schema(description = "天气")
    private String weather;

    @NotNull
    @PastOrPresent
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "日记日期", required = true)
    private Date journalDate;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }
    public String getMoodEmoji() { return moodEmoji; }
    public void setMoodEmoji(String moodEmoji) { this.moodEmoji = moodEmoji; }
    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }
    public Date getJournalDate() { return journalDate; }
    public void setJournalDate(Date journalDate) { this.journalDate = journalDate; }
}
