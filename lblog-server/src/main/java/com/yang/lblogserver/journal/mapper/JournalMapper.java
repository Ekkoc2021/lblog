package com.yang.lblogserver.journal.mapper;

import com.yang.lblogserver.journal.domain.Journal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;

@Mapper
public interface JournalMapper {

    List<Journal> selectByMonth(@Param("userId") Long userId,
                                @Param("startDate") Date startDate,
                                @Param("endDate") Date endDate);

    List<Journal> selectByUserId(@Param("userId") Long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    Journal selectByDate(@Param("userId") Long userId,
                         @Param("date") Date date);

    Journal selectById(@Param("id") Long id,
                       @Param("userId") Long userId);

    int insert(Journal journal);

    int upsert(Journal journal);

    int update(@Param("journal") Journal journal, @Param("userId") Long userId);

    int softDelete(@Param("id") Long id,
                   @Param("userId") Long userId);
}
