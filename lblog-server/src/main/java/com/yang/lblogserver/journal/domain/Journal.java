package com.yang.lblogserver.journal.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.Date;

@Getter
@Setter
@ToString
public class Journal {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String mood;
    private String moodEmoji;
    private String weather;
    private Date journalDate;
    private Boolean isDeleted;
    private Date createdAt;
    private Date updatedAt;
}
