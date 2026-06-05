package com.yang.lblogserver.journal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新日记请求")
public class UpdateJournalRequest {

    @Size(max = 200) private String title;
    private String content;
    @Size(max = 50) private String mood;
    @Size(max = 10) private String moodEmoji;
    @Size(max = 20) private String weather;

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
}
